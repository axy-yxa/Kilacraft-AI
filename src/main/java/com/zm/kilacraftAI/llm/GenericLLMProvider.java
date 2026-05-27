package com.zm.kilacraftAI.llm;

import com.google.gson.*;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.exception.LLMException;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.MessageRoleEnum;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import lombok.Getter;
import okhttp3.*;
import okhttp3.internal.http2.ConnectionShutdownException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 通用 LLM 提供商
 * <p>
 * 支持所有遵循 OpenAI 标准 API 格式的 LLM 厂商（DeepSeek、智谱AI、Moonshot 等）
 * 配置驱动，无需为每个厂商单独编写适配器
 *
 * @author Zm_Mmm
 * @since 2026-04-03
 */
public class GenericLLMProvider implements LLMProvider, ThinkingModelCapable {

    private static final int LOG_TRUNCATE_LENGTH = 50;

    // 性能优化：预分配缓冲区容量
    private static final int BUFFER_SIZE = 8192;
    private static final int INITIAL_RESPONSE_CAPACITY = 512;
    private static final int INITIAL_STREAM_CAPACITY = 256;

    private final KilacraftAI plugin = KilacraftAI.getInstance();
    private final ConfigManager configManager;
    @Getter
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile String cachedApiKey;
    private volatile String cachedApiUrl;
    private volatile String cachedModel;
    private volatile Double cachedTemperature;
    private volatile Integer cachedMaxTokens;
    private volatile Boolean cachedDebugMode;
    private volatile Boolean cachedKnowledgeEnabled;

    public GenericLLMProvider() {
        this.configManager = plugin.getConfigManager();

        // 连接池与 IO_POOL 配合：同步 execute() 每个线程需要一个连接
        // 连接数 = IO_POOL 最大线程数 = min(CPU*4, 128)
        // keepAlive 5分钟：LLM 请求间隔通常较短，复用连接减少 TCP 握手开销
        int ioPoolMaxThreads = Math.min(Runtime.getRuntime().availableProcessors() * 4, 128);
        ConnectionPool connectionPool = new ConnectionPool(ioPoolMaxThreads, 5, TimeUnit.MINUTES);
        // 自动重试失败的连接
        this.httpClient = new OkHttpClient.Builder().connectionPool(connectionPool).connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
        this.gson = new Gson();

        // 初始化配置缓存
        refreshConfigCache();

        // 输出 HTTP 连接池配置日志
        PluginLoggerUtil.info("I/O线程池", "初始化HTTP连接池");
    }

    @Override
    public void refreshConfigCache() {
        this.cachedApiKey = configManager.getLlmApiKey();
        this.cachedApiUrl = configManager.getLlmApiUrl();
        this.cachedModel = configManager.getLlmModel();
        this.cachedTemperature = configManager.getTemperature();
        this.cachedMaxTokens = configManager.getMaxTokens();
        this.cachedDebugMode = configManager.isDebugMode();
        this.cachedKnowledgeEnabled = configManager.isKnowledgeEnabled();
    }

    /**
     * 构建 HTTP 请求（优化：复用 Request.Builder）
     */
    private Request buildRequest(JsonObject requestBody) {
        return new Request.Builder().url(cachedApiUrl).addHeader("Authorization", "Bearer " + cachedApiKey).addHeader("Content-Type", "application/json").addHeader("Accept-Encoding", "gzip").post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json"))).build();
    }

    /**
     * 提取搜索查询关键词
     *
     * @param userMessage 完整的提示词（包含历史、当前输入、执行结果）
     * @return 优化后的搜索查询（空格分隔的关键词）
     */
    private String extractSearchQuery(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return userMessage;
        }

        String contentForExtraction = userMessage;

        // ========== 意图识别场景：提取用户原始输入 ==========
        // 标记可能被翻译（"用户说：" / "User says: "）
        String userSaysMarker = I18nService.tr("用户说：");
        int userInputIndex = contentForExtraction.indexOf(userSaysMarker);
        if (userInputIndex < 0) {
            // 回退：尝试中文原始标记（兼容未翻译场景）
            userInputIndex = contentForExtraction.indexOf("用户说：");
        }
        if (userInputIndex >= 0) {
            String afterMarker = contentForExtraction.substring(userInputIndex + userSaysMarker.length());

            // 查找结束位置：空行或指令词
            int endPosition = afterMarker.length();

            int doubleNewline = afterMarker.indexOf("\n\n");
            if (doubleNewline >= 0) {
                endPosition = doubleNewline;
            }

            // 查找指令词（可能被翻译）
            String pleaseAnalyze = I18nService.tr("请分析");
            int pleaseAnalyzeIdx = afterMarker.indexOf(pleaseAnalyze);
            if (pleaseAnalyzeIdx >= 0 && pleaseAnalyzeIdx < endPosition) {
                endPosition = pleaseAnalyzeIdx;
            }
            // 回退：尝试中文原始
            if (!"请分析".equals(pleaseAnalyze)) {
                int fallback = afterMarker.indexOf("请分析");
                if (fallback >= 0 && fallback < endPosition) {
                    endPosition = fallback;
                }
            }

            contentForExtraction = afterMarker.substring(0, endPosition).trim();

            PluginLoggerUtil.debug("知识库", "提取的内容: {}", contentForExtraction);
            return toSearchQuery(contentForExtraction);
        }

        // ========== LLM 总结分析场景 ==========
        String suffix = plugin.getConfigManager().getAgentAnalysisPromptSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            int suffixIndex = userMessage.indexOf(suffix);
            if (suffixIndex > 0) {
                contentForExtraction = userMessage.substring(0, suffixIndex);
            }
        }

        // 检测统一格式标记（AnalysisSummary 产生的格式，可能被翻译）
        contentForExtraction = extractCleanContent(contentForExtraction);

        PluginLoggerUtil.debug("知识库", "提取的内容: {}", contentForExtraction);
        return toSearchQuery(contentForExtraction);
    }

    /**
     * 根据当前语言选择关键词提取策略
     */
    private String toSearchQuery(String content) {
        int keywordTopK = configManager.getKeywordTopK();
        return TextProcessorFactory.get().toSearchQuery(content, keywordTopK);
    }

    /**
     * 从统一格式的摘要中提取干净的内容用于关键词提取
     *
     * <p>保留用户输入 + 执行结果的实际数据，去除结构化标记和颜色代码</p>
     *
     * @param content 截取后的业务内容
     * @return 干净的文本内容
     */
    private String extractCleanContent(String content) {
        // 检测统一格式标记（可能被翻译，同时尝试中英文版本）
        String resultsMarker = I18nService.tr("[执行结果]");
        if (!content.contains(resultsMarker)) {
            resultsMarker = "[执行结果]"; // 回退中文原始
        }
        if (!content.contains(resultsMarker)) {
            return content;
        }

        // 提取 [用户输入] 部分的内容
        StringBuilder cleanContent = new StringBuilder();
        String userInputMarker = I18nService.tr("[用户输入]");
        int userInputStart = content.indexOf(userInputMarker);
        if (userInputStart < 0) {
            userInputStart = content.indexOf("[用户输入]"); // 回退
        }
        if (userInputStart >= 0) {
            int textStart = userInputStart + userInputMarker.length();
            int textEnd = findNextMarker(content, textStart);
            String userInput = content.substring(textStart, textEnd).trim();
            if (!userInput.isEmpty()) {
                cleanContent.append(userInput).append(" ");
            }
        }

        // 提取 [执行结果] 部分的实际数据（去除结构标记和颜色代码）
        int resultsStart = content.indexOf(resultsMarker);
        if (resultsStart >= 0) {
            int textStart = resultsStart + resultsMarker.length();
            String statsMarker = I18nService.tr("[统计]");
            int textEnd = content.indexOf(statsMarker);
            if (textEnd < 0) textEnd = content.indexOf("[统计]");
            if (textEnd < 0) textEnd = content.length();

            String resultsSection = content.substring(textStart, textEnd);
            // 去除 step_id、状态标签、颜色代码、行首标记
            String cleaned = resultsSection.replaceAll("-\\s*step_\\w+:\\s*", "").replaceAll("-\\s*(?=\\[)", "").replaceAll("\\[(SUCCESS|FAILURE|SKIPPED|UNKNOWN)]\\s*", "").replaceAll("§[0-9a-fk-orA-FK-OR]", "").replaceAll("^[\\s\\-]+", "").replaceAll("\\n\\s*-\\s*", " ").trim();
            // 去除坐标/数值型数据（如 x: -11.00, y: 97.00），这些对知识库检索无语义价值
            cleaned = cleaned.replaceAll("\\b(x|y|z|pitch|yaw):\\s*-?\\d+(\\.\\d+)?\\b", "").trim();
            if (!cleaned.isEmpty()) {
                cleanContent.append(cleaned);
            }
        }

        return cleanContent.toString().trim();
    }

    /**
     * 查找下一个格式标记的位置
     */
    private int findNextMarker(String content, int fromIndex) {
        // 同时查找翻译后和中文原始的标记
        String[] markers = {I18nService.tr("[任务目标]"), I18nService.tr("[执行结果]"), I18nService.tr("[统计]"), "[任务目标]", "[执行结果]", "[统计]"};
        int nearest = content.length();
        for (String marker : markers) {
            int idx = content.indexOf(marker, fromIndex);
            if (idx >= fromIndex && idx < nearest) {
                nearest = idx;
            }
        }
        return nearest;
    }

    /**
     * 打印调试日志（优化：减少字符串拼接）
     */
    private void printDebugLog(String playerName, String userMessage, Deque<ConversationManager.Message> history) {
        PluginLoggerUtil.debug("LLM请求", "========== API 请求开始 ==========");
        PluginLoggerUtil.debug("LLM请求", "玩家：{}", playerName);
        PluginLoggerUtil.debug("LLM请求", "当前消息：{}", userMessage);
        PluginLoggerUtil.debug("LLM请求", "模型：{}", cachedModel);
        PluginLoggerUtil.debug("LLM请求", "URL: {}", cachedApiUrl);
        PluginLoggerUtil.debug("LLM请求", "温度：{}", cachedTemperature);
        PluginLoggerUtil.debug("LLM请求", "最大 Token:{}", cachedMaxTokens);

        // 打印历史记录信息
        if (history != null && !history.isEmpty()) {
            PluginLoggerUtil.debug("LLM请求", "历史对话数量：{} 条", history.size());
            int index = 0;
            for (ConversationManager.Message msg : history) {
                index++;
                String content = msg.getContent();
                // AI 回答的历史记录只打印前若干个字符，避免日志过长
                if (MessageRoleEnum.ASSISTANT.value().equals(msg.getRole()) && content.length() > LOG_TRUNCATE_LENGTH) {
                    content = content.substring(0, LOG_TRUNCATE_LENGTH) + I18nService.tr("... (共{} 字符)", msg.getContent().length());
                }
                PluginLoggerUtil.debug("LLM请求", "历史 [{}] ({}): {}", index, msg.getRole(), content);
            }
        } else {
            PluginLoggerUtil.debug("LLM请求", "历史对话：无");
        }
    }

    @Override
    public CompletableFuture<String> processRequest(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler) {
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, configManager.getSystemPrompt(), true, true);
    }

    @Override
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt) {
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, true, true);
    }

    @Override
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog) {
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, enableKnowledgeRetrieval, enableDebugLog, false);
    }

    @Override
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog, boolean enableJsonOutput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 打印调试日志
                if (enableDebugLog && cachedDebugMode) {
                    printDebugLog(playerName, userMessage, history);
                }

                // ========== 知识检索增强 ==========
                String enhancedUserMessage = userMessage;

                // 检查知识库和知识检索开关
                if (enableKnowledgeRetrieval && cachedKnowledgeEnabled) {
                    var knowledgeRetriever = plugin.getKnowledgeRetriever();

                    if (knowledgeRetriever != null) {
                        // 提取搜索查询关键词
                        String searchQuery = extractSearchQuery(userMessage);

                        // 检索相关知识
                        var relevantKnowledge = knowledgeRetriever.retrieveKnowledge(searchQuery);

                        // 如果有相关知识，添加到上下文中
                        if (!relevantKnowledge.isEmpty()) {
                            String knowledgeContext = knowledgeRetriever.formatAsContext(relevantKnowledge);
                            enhancedUserMessage = knowledgeContext + "\n" + I18nService.tr("用户问题：") + userMessage;
                            PluginLoggerUtil.debug("LLM请求", "已添加 {} 个知识片段到上下文中", relevantKnowledge.size());
                        }
                    }
                }
                // ===================================

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", cachedModel);
                requestBody.addProperty("temperature", cachedTemperature);
                requestBody.addProperty("max_tokens", cachedMaxTokens);
                // 始终使用流式请求
                requestBody.addProperty("stream", true);

                // 启用 JSON 输出格式（仅意图识别阶段使用）
                if (enableJsonOutput) {
                    JsonObject responseFormat = new JsonObject();
                    responseFormat.addProperty("type", "json_object");
                    requestBody.add("response_format", responseFormat);
                }

                JsonArray messages = new JsonArray();

                // 使用自定义的系统提示词，替换 {player} 占位符
                String systemPrompt = customSystemPrompt.replace("{player}", playerName);

                // 语言约束：强制 AI 输出使用服务器配置的语言
                // 防止第三方 SPI Skill 的多语言数据干扰输出语言
                String langDirective = plugin.getConfigManager().getLanguageDirective();
                if (langDirective != null) {
                    systemPrompt += "\n" + langDirective;
                }

                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", MessageRoleEnum.SYSTEM.value());
                systemMessage.addProperty("content", systemPrompt);
                messages.add(systemMessage);

                // 添加历史对话记录
                if (history != null && !history.isEmpty()) {
                    for (ConversationManager.Message msg : history) {
                        JsonObject msgObj = new JsonObject();
                        msgObj.addProperty("role", msg.getRole());
                        msgObj.addProperty("content", msg.getContent());
                        messages.add(msgObj);
                    }
                }

                // 用户消息
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", MessageRoleEnum.USER.value());
                userMsg.addProperty("content", enhancedUserMessage);
                messages.add(userMsg);

                requestBody.add("messages", messages);

                Request request = buildRequest(requestBody);

                // ========== 使用流式读取 ==========
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        // 检测是否为 response_format 不支持的错误
                        String errorMsg = "§c" + I18nService.tr("请求失败：{} - {}", String.valueOf(response.code()), response.message());
                        if (enableJsonOutput && response.code() == 400 && response.message().contains("response_format")) {
                            // 自动降级：重新调用，不启用 JSON 输出
                            PluginLoggerUtil.warn("LLM请求", "当前 LLM 不支持 response_format，自动降级为普通模式");
                            return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, enableKnowledgeRetrieval, enableDebugLog, false).join();
                        }
                        return errorMsg;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        return "§c" + I18nService.tr("API 响应为空");
                    }

                    // 预分配容量，减少扩容开销
                    StringBuilder fullResponse = new StringBuilder(INITIAL_RESPONSE_CAPACITY);
                    StringBuilder currentStreamMessage = new StringBuilder(INITIAL_STREAM_CAPACITY);

                    // 使用 BufferedReader 逐行流式读取（始终使用 SSE 模式）
                    try (BufferedReader reader = new BufferedReader(body.charStream(), BUFFER_SIZE)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 跳过空行
                            if (line.isEmpty()) {
                                continue;
                            }

                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);

                                // 检查结束标志
                                if ("[DONE]".equals(data.trim())) {
                                    break;
                                }

                                try {
                                    JsonObject json = gson.fromJson(data, JsonObject.class);
                                    if (json == null) {
                                        continue;
                                    }

                                    JsonArray choices = json.getAsJsonArray("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        JsonObject choice = choices.get(0).getAsJsonObject();
                                        if (choice == null) {
                                            continue;
                                        }

                                        JsonObject delta = choice.getAsJsonObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            JsonElement contentElement = delta.get("content");
                                            if (contentElement == null || contentElement.isJsonNull()) {
                                                continue;
                                            }
                                            String content = contentElement.getAsString();
                                            if (content.isEmpty()) {
                                                continue;
                                            }

                                            fullResponse.append(content);

                                            // 如果 handler 开启流式输出，实时回调
                                            if (responseHandler != null && responseHandler.isStreamOutputEnabled()) {
                                                currentStreamMessage.append(content);
                                                responseHandler.showStreamChunk(content, currentStreamMessage.toString());
                                            }
                                        }
                                    }
                                } catch (JsonParseException e) {
                                    // 忽略 JSON 解析错误，继续处理下一行
                                    if (cachedDebugMode) {
                                        String truncatedData = data.length() > 100 ? data.substring(0, 100) + "..." : data;
                                        PluginLoggerUtil.debug("LLM请求", "JSON 解析失败：{}", truncatedData);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        if (e instanceof ConnectionShutdownException) {
                            PluginLoggerUtil.debug("LLM请求", "连接被对端关闭");
                        } else {
                            PluginLoggerUtil.warn("LLM请求", "读取响应流失败", e);
                        }
                        return "§c" + I18nService.tr("读取响应失败：{}", e.getMessage());
                    }

                    // 流式输出完成后，调用 showResponse() 触发 completeGeneration()
                    if (responseHandler != null) {
                        responseHandler.showResponse(fullResponse.toString());
                    }

                    return fullResponse.toString();
                }

            } catch (Exception e) {
                PluginLoggerUtil.error("LLM请求", "LLM 请求失败", e);
                if (cachedDebugMode) {
                    PluginLoggerUtil.debug("LLM请求", "错误详情：{}", e.getMessage());
                }
                String errorMsg = "§c" + I18nService.tr("请求失败：{}", e.getMessage());
                if (responseHandler != null) {
                    responseHandler.handleError(errorMsg);
                }
                return errorMsg;
            } finally {
                if (enableDebugLog && cachedDebugMode) {
                    PluginLoggerUtil.debug("LLM请求", "========== API 请求结束 ==========");
                }
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 使用推理模型处理请求（非流式）
     *
     * <p>专用于服务器诊断场景，不对外开放。特性：</p>
     * <ul>
     *   <li>非流式输出（诊断报告生成到文件，无需实时流式）</li>
     *   <li>自动适配 DeepSeek-R1 ({@code thinking} 参数) / OpenAI o1-o3-o4 ({@code max_completion_tokens}，无 system 消息) 格式差异</li>
     *   <li>从响应中提取 {@code reasoning_content}（思考过程）</li>
     * </ul>
     *
     * <p>此方法为 GenericLLMProvider 独有方法（非 LLMProvider 接口方法），
     * 调用方通过 {@code instanceof ThinkingModelCapable} 检查能力。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param config       推理模型配置（API 地址、密钥、模型名等）
     * @param client       推理模型专用 HTTP 客户端（由 AdminConfigManager 提供，共享连接池）
     * @return LLM 响应（含推理过程和用量）
     * @throws LLMException 请求或解析失败时抛出
     */
    public LLMResponse processRequestWithThinkingModel(String systemPrompt, String userMessage, ThinkingModelConfig config, OkHttpClient client) throws LLMException {
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.model());
        requestBody.addProperty("stream", false); // 非流式

        // DeepSeek 特有参数：启用思考模式
        if (config.model().contains("deepseek")) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            requestBody.add("thinking", thinking);
        }

        // 构建消息数组
        JsonArray messages = new JsonArray();

        // OpenAI o1/o3/o4 系列不支持 system 消息，需合并到 user 消息
        boolean isOpenAIOModel = config.model().startsWith("o1") || config.model().startsWith("o3") || config.model().startsWith("o4");
        if (isOpenAIOModel) {
            // o 系列使用 max_completion_tokens（替代 max_tokens）
            requestBody.addProperty("max_completion_tokens", config.maxTokens());
            // system 消息合并到 user 消息
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", MessageRoleEnum.USER.value());
            userMsg.addProperty("content", systemPrompt + "\n\n" + userMessage);
            messages.add(userMsg);
        } else {
            requestBody.addProperty("max_tokens", config.maxTokens());
            // 正常模型：system + user 分开
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", MessageRoleEnum.SYSTEM.value());
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", MessageRoleEnum.USER.value());
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);
        }
        requestBody.add("messages", messages);

        // 发送 HTTP 请求
        Request request = new Request.Builder().url(config.apiUrl()).addHeader("Authorization", "Bearer " + config.apiKey()).addHeader("Content-Type", "application/json").post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json"))).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new LLMException("推理模型请求失败: HTTP " + response.code() + " - " + errorBody);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new LLMException("推理模型响应为空");
            }

            String responseBody = body.string();
            return parseThinkingModelResponse(responseBody);
        } catch (IOException e) {
            throw new LLMException("推理模型请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析推理模型响应（提取 content + reasoning_content + usage）
     */
    private LLMResponse parseThinkingModelResponse(String responseBody) throws LLMException {
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json == null) {
                throw new LLMException("响应 JSON 为空");
            }

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LLMException("响应中无 choices");
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                throw new LLMException("响应中无 message");
            }

            // 提取 content
            String content = message.has("content") && !message.get("content").isJsonNull() ? message.get("content").getAsString() : "";

            // 提取 reasoning_content（DeepSeek-R1 等模型的思考过程）
            String reasoningContent = null;
            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                reasoningContent = message.get("reasoning_content").getAsString();
            }

            // 提取 usage
            int promptTokens = -1;
            int completionTokens = -1;
            if (json.has("usage") && !json.get("usage").isJsonNull()) {
                JsonObject usage = json.getAsJsonObject("usage");
                if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").getAsInt();
                if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").getAsInt();
            }

            return new LLMResponse(content, reasoningContent, promptTokens, completionTokens);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("解析推理模型响应失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        try {
            // 取消所有正在执行的请求
            httpClient.dispatcher().cancelAll();

            // 关闭 ExecutorService
            httpClient.dispatcher().executorService().shutdown();

            // 等待任务终止(最多5秒)
            if (!httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                PluginLoggerUtil.warn("I/O线程池", "HTTP客户端未能在5秒内完全关闭,强制关闭");
                httpClient.dispatcher().executorService().shutdownNow();
            }

            // 清空连接池
            httpClient.connectionPool().evictAll();

            PluginLoggerUtil.info("I/O线程池", "HTTP连接池已关闭");
        } catch (Exception e) {
            PluginLoggerUtil.error("I/O线程池", "关闭HTTP客户端时发生错误", e);
        }
    }
}
