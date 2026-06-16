package com.zm.kilacraftAI.llm;

import com.google.gson.*;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.MessageRoleEnum;
import com.zm.kilacraftAI.common.exception.EmptyResponseException;
import com.zm.kilacraftAI.common.exception.LLMException;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import okhttp3.*;
import okhttp3.internal.http2.ConnectionShutdownException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
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
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * 获取共享的 HTTP 客户端（实现 ThinkingModelCapable 接口）
     */
    @Override
    public OkHttpClient getSharedHttpClient() {
        return httpClient;
    }

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

                // 构建 SSE 流式请求体
                JsonObject requestBody = buildRequestBody(enhancedUserMessage, history, customSystemPrompt, playerName, enableJsonOutput);

                // 构建 HTTP 请求
                Request request = buildRequest(requestBody);

                // ========== 使用流式读取 ==========
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        // 读取错误响应体用于降级判断。注意：response.message() 只是 HTTP reason phrase
                        // （通常是 "Bad Request"），不含厂商错误详情，无法据此判断是否因 response_format 报错。
                        String errorBody = "";
                        try {
                            if (response.body() != null) {
                                errorBody = response.body().string();
                            }
                        } catch (IOException ignored) {
                            // 读取错误体失败不影响后续错误提示
                        }
                        // 检测是否为 response_format 不支持的错误（错误体含 response_format 字样）
                        if (enableJsonOutput && response.code() == 400 && errorBody.contains("response_format")) {
                            // 自动降级：重新调用，不启用 JSON 输出。
                            // enableJsonOutput=false 后不会再进入此分支（天然防递归）。
                            PluginLoggerUtil.warn("LLM请求", "当前 LLM 不支持 response_format，自动降级为普通模式");
                            return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, enableKnowledgeRetrieval, enableDebugLog, false).join();
                        }
                        PluginLoggerUtil.debug("LLM请求", "错误响应体: {}", errorBody);
                        // 错误体仅用于诊断，不暴露给玩家；玩家看到的是 code + reason phrase
                        return "§c" + I18nService.tr("请求失败：{} - {}", String.valueOf(response.code()), response.message());
                    }

                    // 解析 SSE 流式响应
                    return parseSSEStream(response, responseHandler);
                }

            } catch (EmptyResponseException e) {
                // 空响应：走错误处理路径（handleError 会 cancelStream 清理流式状态 + sendError 友好提示）。
                // 不复用 catch(Exception) 的"LLM 请求失败"措辞与 error 堆栈——空响应是模型空输出而非请求失败，
                // 其可观测性已由 parseSSEStream 的 warn 日志覆盖。
                String errorMsg = "§c" + e.getMessage();
                if (responseHandler != null) {
                    responseHandler.handleError(errorMsg);
                }
                return errorMsg;
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
     * 构建 SSE 流式请求体
     *
     * @param enhancedUserMessage 增强后的用户消息（可能包含知识库上下文）
     * @param history             历史对话记录
     * @param customSystemPrompt  自定义系统提示词
     * @param playerName          玩家名称
     * @param enableJsonOutput    是否启用 JSON 输出格式
     * @return 构建好的请求体 JSON
     */
    private JsonObject buildRequestBody(String enhancedUserMessage, Deque<ConversationManager.Message> history, String customSystemPrompt, String playerName, boolean enableJsonOutput) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", cachedModel);
        requestBody.addProperty("temperature", cachedTemperature);
        // JSON 输出场景（意图识别、画像分析）不设 max_tokens，避免复杂 JSON 被截断导致解析失败
        // 自然语言输出场景（普通对话、通知、广播、问候）使用配置值，限制输出长度
        if (!enableJsonOutput) {
            requestBody.addProperty("max_tokens", cachedMaxTokens);
        }
        // 始终使用流式请求
        requestBody.addProperty("stream", true);

        // 启用 JSON 输出格式（仅意图识别阶段使用）
        if (enableJsonOutput) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            requestBody.add("response_format", responseFormat);
        }

        // 思考模式治理：对默认开启思考的模型，显式关闭思考模式
        // 思考 token 与输出 token 共享 max_tokens 预算（默认 600），
        // 若不关闭，思考过程会耗尽配额导致实际输出为空
        disableThinkingIfNeeded(requestBody);

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
        return requestBody;
    }

    /**
     * 对默认开启思考/推理模式的模型，显式关闭思考模式。
     * <p>
     * 思考 token 与输出 token 共享 max_tokens 预算，
     * MC 场景 max_tokens 较小（默认 600），思考模式会导致实际输出为空。
     * <p>
     * 参数格式分四组：
     * <ul>
     *   <li>Group 1 — {@code thinking: {type: "disabled"}} (object):
     *       MiMo, DeepSeek V4+, GLM 4.5+, Kimi K2+</li>
     *   <li>Group 2 — {@code enable_thinking: false} (boolean):
     *       Qwen3 开源 / Qwen3.5+ 商业版</li>
     *   <li>Group 3 — {@code reasoning_effort: "none"} (string):
     *       xAI Grok 4, Mistral (medium-3-5 / small)</li>
     *   <li>Group 4 — {@code thinking: "disabled"} (string):
     *       Doubao thinking/seed, MiniMax-M3</li>
     * </ul>
     * <p>
     * 不处理的模型：
     * <ul>
     *   <li>始终思考不可关闭: deepseek-reasoner, QwQ, GLM-Z1, OpenAI o-series, Gemini 2.5 Pro</li>
     *   <li>默认关闭或不支持: deepseek-chat (V3), qwen-turbo/plus, doubao-lite, glm-4-flash,
     *       gpt-4o, llama, grok-3, moonshot-v1, step-3.x, yi-lightning 等</li>
     * </ul>
     */
    private void disableThinkingIfNeeded(JsonObject requestBody) {
        String model = cachedModel.toLowerCase(java.util.Locale.ROOT);

        // ── Group 1: thinking: {type: "disabled"} (object format) ──
        // MiMo、DeepSeek V4+、GLM 4.5+、Kimi K2+ 均使用此格式
        if (isThinkingObjectModel(model)) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "disabled");
            requestBody.add("thinking", thinking);
            return;
        }

        // ── Group 2: enable_thinking: false ──
        // Qwen3 开源系列、Qwen3.5/3.6/3.7 商业版默认开启思考
        if (model.contains("qwen3") || model.contains("qwen-3")) {
            requestBody.addProperty("enable_thinking", false);
            return;
        }

        // ── Group 3: reasoning_effort: "none" ──
        // xAI Grok 4.x 默认 low 推理；Mistral medium-3-5 / small 支持 reasoning_effort
        if (model.contains("grok-4") || model.startsWith("grok-3-mini")) {
            requestBody.addProperty("reasoning_effort", "none");
            return;
        }

        // ── Group 4: thinking: "disabled" (string format) ──
        // Doubao thinking/seed 系列使用字符串格式；MiniMax-M3 支持 adaptive/disabled
        if ((model.contains("doubao") && (model.contains("thinking") || model.contains("seed"))) || model.contains("minimax-m3")) {
            requestBody.addProperty("thinking", "disabled");
        }
    }

    /**
     * 判断模型是否使用 thinking: {type: "disabled"} (object) 格式
     * <p>
     * 覆盖: MiMo 全系列、DeepSeek V4+ (V3.x 默认 OFF 无需处理)、GLM 4.5+、Kimi K2+
     */
    private boolean isThinkingObjectModel(String model) {
        // Xiaomi MiMo
        if (model.contains("mimo")) return true;
        // DeepSeek V4+ (排除 reasoner — 始终思考不可关闭，排除 chat — V3.x 默认 OFF)
        if (model.contains("deepseek") && model.contains("v4")) return true;
        // Zhipu GLM 4.5 / 4.6 / 4.7 / 5.x (排除 glm-4-flash/air/plus/long — 不支持思考)
        if (model.contains("glm-4.5") || model.contains("glm-4.6") || model.contains("glm-4.7") || model.contains("glm-5"))
            return true;
        // Moonshot Kimi K2+ (排除 moonshot-v1 — 不支持思考)
        if (model.contains("kimi")) return true;
        return false;
    }

    /**
     * 为 admin 推理模型路径显式启用思考模式。
     * <p>
     * 按模型族注入对应的启用参数：
     * <ul>
     *   <li>thinking: {type: "enabled"} (object): DeepSeek, MiMo, GLM 4.5+, Kimi K2+</li>
     *   <li>enable_thinking: true (boolean): Qwen3 / QwQ</li>
     *   <li>reasoning_effort: "high" (string): xAI Grok 4, Mistral medium/small</li>
     *   <li>thinking: "enabled" (string): Doubao thinking/seed</li>
     * </ul>
     * 不需要显式启用的模型（始终思考）: deepseek-reasoner, QwQ, GLM-Z1, o-series
     */
    private void enableThinkingForModel(String modelName, JsonObject requestBody) {
        String model = modelName.toLowerCase(java.util.Locale.ROOT);

        // ── thinking: {type: "enabled"} (object format) ──
        if (model.contains("deepseek") || model.contains("mimo") || model.contains("kimi") || model.contains("glm-4.5") || model.contains("glm-4.6") || model.contains("glm-4.7") || model.contains("glm-5")) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            requestBody.add("thinking", thinking);
            return;
        }

        // ── enable_thinking: true ──
        if (model.contains("qwen3") || model.contains("qwen-3")) {
            requestBody.addProperty("enable_thinking", true);
            return;
        }

        // ── reasoning_effort: "high" ──
        if (model.contains("grok-4") || model.startsWith("grok-3-mini") || model.contains("mistral-medium") || model.contains("mistral-small")) {
            requestBody.addProperty("reasoning_effort", "high");
            return;
        }

        // ── thinking: "enabled" (string format) ──
        if (model.contains("doubao") && (model.contains("thinking") || model.contains("seed"))) {
            requestBody.addProperty("thinking", "enabled");
        }
    }

    /**
     * 构建标准的 system + user 消息对
     */
    private void addSystemAndUserMessages(JsonArray messages, String systemPrompt, String userMessage) {
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", MessageRoleEnum.SYSTEM.value());
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", MessageRoleEnum.USER.value());
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
    }

    /**
     * 解析 SSE 流式响应
     *
     * @param response        HTTP 响应
     * @param responseHandler 响应处理器（流式回调）
     * @return 完整的响应文本
     */
    private String parseSSEStream(Response response, AIResponseHandler responseHandler) {
        ResponseBody body = response.body();
        if (body == null) {
            return "§c" + I18nService.tr("API 响应为空");
        }

        // 预分配容量，减少扩容开销
        StringBuilder fullResponse = new StringBuilder(INITIAL_RESPONSE_CAPACITY);
        StringBuilder currentStreamMessage = new StringBuilder(INITIAL_STREAM_CAPACITY);

        // 收集原始 SSE 数据行（用于空响应时诊断）
        int chunkCount = 0;
        final Deque<String> recentRawChunks = new ArrayDeque<>(3);

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

                    chunkCount++;
                    // 保留最近 3 条原始 chunk 用于诊断
                    if (recentRawChunks.size() >= 3) recentRawChunks.pollFirst();
                    recentRawChunks.addLast(data.length() > 200 ? data.substring(0, 200) + "..." : data);

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

        // 空响应检测：抛出异常走错误处理路径（handleError），避免玩家收到空消息，
        // 也避免上层（意图识别 / 二次分析 / 历史持久化）把降级提示误认为真实回复。
        String result = fullResponse.toString();
        if (result.isEmpty()) {
            PluginLoggerUtil.warn("LLM请求", I18nService.tr("LLM 返回空响应，共收到 {} 个 SSE chunk，最近原始数据: {}", chunkCount, String.join(" | ", recentRawChunks)));
            throw new EmptyResponseException(I18nService.tr("AI 暂时无法回复，请稍后再试"));
        }

        // 流式输出完成后，调用 showResponse() 触发 completeGeneration()
        if (responseHandler != null) {
            responseHandler.showResponse(result);
        }

        return result;
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
     * <p>此方法为 {@link ThinkingModelCapable} 接口方法，通过 {@code instanceof ThinkingModelCapable} 检查能力。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param config       推理模型配置（API 地址、密钥、模型名等）
     * @param client       推理模型专用 HTTP 客户端（由 AdminConfigManager 提供，共享连接池）
     * @return LLM 响应（含推理过程和用量）
     * @throws LLMException 请求或解析失败时抛出
     */
    @Override
    public LLMResponse processRequestWithThinkingModel(String systemPrompt, String userMessage, ThinkingModelConfig config, OkHttpClient client) throws LLMException {
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.model());
        requestBody.addProperty("stream", false); // 非流式

        // ── 思考模式参数：按模型族注入启用思考的参数 ──
        enableThinkingForModel(config.model(), requestBody);

        // 构建消息数组
        JsonArray messages = new JsonArray();

        // ── 消息格式与 max_tokens 参数：按模型族适配 ──
        // OpenAI o1/o3/o4 系列不支持 system 消息，需合并到 user 消息
        boolean isOpenAIOModel = config.model().startsWith("o1") || config.model().startsWith("o3") || config.model().startsWith("o4");
        // Doubao thinking/seed 系列使用 max_completion_tokens（不能与 max_tokens 同时设置）
        boolean isDoubaoThinkingModel = config.model().contains("doubao") && (config.model().contains("thinking") || config.model().contains("seed"));

        if (isOpenAIOModel) {
            requestBody.addProperty("max_completion_tokens", config.maxTokens());
            // o 系列：显式请求推理摘要（默认可能不返回 reasoning.summary 文本，需声明 summary 参数）
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "medium");
            reasoning.addProperty("summary", "auto");
            requestBody.add("reasoning", reasoning);
            // o 系列：system 消息合并到 user 消息（不支持 system role）
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", MessageRoleEnum.USER.value());
            userMsg.addProperty("content", systemPrompt + "\n\n" + userMessage);
            messages.add(userMsg);
        } else if (isDoubaoThinkingModel) {
            requestBody.addProperty("max_completion_tokens", config.maxTokens());
            // Doubao：正常 system + user 分开
            addSystemAndUserMessages(messages, systemPrompt, userMessage);
        } else {
            requestBody.addProperty("max_tokens", config.maxTokens());
            // 其他模型：正常 system + user 分开
            addSystemAndUserMessages(messages, systemPrompt, userMessage);
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

            // 提取推理过程：
            // - DeepSeek-R1 等使用 message.reasoning_content（字符串）
            // - OpenAI o 系列使用 message.reasoning.summary[]（需在请求时带 summary 参数才会返回）
            String reasoningContent = null;
            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                reasoningContent = message.get("reasoning_content").getAsString();
            } else if (message.has("reasoning") && message.get("reasoning").isJsonObject()) {
                reasoningContent = extractOpenAIReasoningSummary(message.getAsJsonObject("reasoning"));
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

    /**
     * 提取 OpenAI o 系列的推理摘要（{@code message.reasoning.summary[]}）。
     *
     * <p>o 系列的 summary 是一组 {@code {type, text}} 对象，按顺序拼接为可读文本。
     * 需在请求时声明 {@code reasoning.summary} 参数，响应中才会包含此字段。</p>
     *
     * @param reasoning message.reasoning 对象
     * @return 拼接后的摘要文本；无摘要时返回 null
     */
    private String extractOpenAIReasoningSummary(JsonObject reasoning) {
        if (reasoning == null || !reasoning.has("summary") || !reasoning.get("summary").isJsonArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (var el : reasoning.getAsJsonArray("summary")) {
            if (!el.isJsonObject()) continue;
            JsonObject item = el.getAsJsonObject();
            if (item.has("text") && !item.get("text").isJsonNull()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(item.get("text").getAsString());
            }
        }
        return !sb.isEmpty() ? sb.toString() : null;
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
