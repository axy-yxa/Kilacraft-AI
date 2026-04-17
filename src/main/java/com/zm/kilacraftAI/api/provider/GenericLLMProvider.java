package com.zm.kilacraftAI.api.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.util.ChineseTextUtil;
import com.zm.kilacraftAI.util.PluginLogger;
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
public class GenericLLMProvider implements LLMProvider {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final int LOG_TRUNCATE_LENGTH = 50;

    // 性能优化：预分配缓冲区容量
    private static final int BUFFER_SIZE = 8192;
    private static final int INITIAL_RESPONSE_CAPACITY = 512;
    private static final int INITIAL_STREAM_CAPACITY = 256;

    private final KilacraftAI plugin = KilacraftAI.getInstance();
    private final ConfigManager configManager;
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

        // 配置连接池，复用 HTTP 连接
        ConnectionPool connectionPool = new ConnectionPool(10, 5, TimeUnit.MINUTES);
        // 自动重试失败的连接
        this.httpClient = new OkHttpClient.Builder().connectionPool(connectionPool).connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
        this.gson = new Gson();

        // 初始化配置缓存
        refreshConfigCache();

        // 输出 HTTP 连接池配置日志
        PluginLogger.info("LLM提供商", "初始化HTTP连接池");
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

        // ========== 意图识别 ==========
        int userInputIndex = contentForExtraction.indexOf("用户说：");
        if (userInputIndex >= 0) {
            // 提取“用户说：”后面的内容，直到遇到空行或特定指令词
            String afterMarker = contentForExtraction.substring(userInputIndex + "用户说：".length());

            // 查找结束位置：空行（\n\n）或“请分析”等指令词
            int endPosition = afterMarker.length();

            // 查找空行
            int doubleNewline = afterMarker.indexOf("\n\n");
            if (doubleNewline >= 0) {
                endPosition = doubleNewline;
            }

            // 查找“请分析”（意图识别的指令词）
            int pleaseAnalyze = afterMarker.indexOf("请分析");
            if (pleaseAnalyze >= 0 && pleaseAnalyze < endPosition) {
                endPosition = pleaseAnalyze;
            }

            contentForExtraction = afterMarker.substring(0, endPosition).trim();

            // 直接返回用户真实输入，不再进行后续处理
            int keywordTopK = configManager.getKeywordTopK();
            return ChineseTextUtil.toSearchQuery(contentForExtraction, keywordTopK);
        }

        // ========== LLM 总结分析 ==========
        String suffix = plugin.getConfigManager().getAgentAnalysisPromptSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            int suffixIndex = userMessage.indexOf(suffix);
            if (suffixIndex > 0) {
                // 截取 suffix 之前的内容（包含用户输入和执行结果）
                contentForExtraction = userMessage.substring(0, suffixIndex);
            }
        }

        // 检测统一格式标记（AnalysisSummary 产生的格式）
        contentForExtraction = extractCleanContent(contentForExtraction);

        // 使用配置的 topK 参数
        int keywordTopK = configManager.getKeywordTopK();
        return ChineseTextUtil.toSearchQuery(contentForExtraction, keywordTopK);
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
        // 检测统一格式标记
        if (!content.contains("[执行结果]")) {
            return content;
        }

        // 提取 [用户输入] 部分的内容
        StringBuilder cleanContent = new StringBuilder();
        int userInputStart = content.indexOf("[用户输入]");
        if (userInputStart >= 0) {
            int textStart = userInputStart + "[用户输入]".length();
            int textEnd = findNextMarker(content, textStart);
            String userInput = content.substring(textStart, textEnd).trim();
            if (!userInput.isEmpty()) {
                cleanContent.append(userInput).append(" ");
            }
        }

        // 提取 [执行结果] 部分的实际数据（去除结构标记和颜色代码）
        int resultsStart = content.indexOf("[执行结果]");
        if (resultsStart >= 0) {
            int textStart = resultsStart + "[执行结果]".length();
            int textEnd = content.indexOf("[统计]");
            if (textEnd < 0) textEnd = content.length();

            String resultsSection = content.substring(textStart, textEnd);
            // 去除 step_id、状态标签、颜色代码、行首标记
            String cleaned = resultsSection.replaceAll("-\\s*step_\\w+:\\s*", "")  // 去除 "- step_1: "
                    .replaceAll("-\\s*(?=\\[)", "")            // 去除行首 "- "（在状态标签前）
                    .replaceAll("\\[(SUCCESS|FAILURE|SKIPPED|UNKNOWN)]\\s*", "") // 去除状态标签
                    .replaceAll("§[0-9a-fk-orA-FK-OR]", "") // 去除颜色代码
                    .replaceAll("^[\\s\\-]+", "")            // 去除开头的空白和连字符
                    .replaceAll("\\n\\s*-\\s*", " ")       // 后续行的 "- " 替换为空格
                    .trim();
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
        String[] markers = {"[任务目标]", "[执行结果]", "[统计]"};
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
        PluginLogger.debug("LLM请求", "========== API 请求开始 ==========");
        PluginLogger.debug("LLM请求", "玩家：" + playerName);
        PluginLogger.debug("LLM请求", "当前消息：" + userMessage);
        PluginLogger.debug("LLM请求", "模型：" + cachedModel);
        PluginLogger.debug("LLM请求", "URL：" + cachedApiUrl);
        PluginLogger.debug("LLM请求", "温度：" + cachedTemperature);
        PluginLogger.debug("LLM请求", "最大 Token:" + cachedMaxTokens);

        // 打印历史记录信息
        if (history != null && !history.isEmpty()) {
            PluginLogger.debug("LLM请求", "历史对话数量：" + history.size() + " 条");
            int index = 0;
            for (ConversationManager.Message msg : history) {
                index++;
                String content = msg.getContent();
                // AI 回答的历史记录只打印前若干个字符，避免日志过长
                if (ROLE_ASSISTANT.equals(msg.getRole()) && content.length() > LOG_TRUNCATE_LENGTH) {
                    content = content.substring(0, LOG_TRUNCATE_LENGTH) + "... (共" + msg.getContent().length() + " 字符)";
                }
                PluginLogger.debug("LLM请求", "历史 [" + index + "] (" + msg.getRole() + ")：" + content);
            }
        } else {
            PluginLogger.debug("LLM请求", "历史对话：无");
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
                            enhancedUserMessage = knowledgeContext + "\n用户问题：" + userMessage;
                            PluginLogger.debug("LLM请求", "已添加 " + relevantKnowledge.size() + " 个知识片段到上下文中");
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

                JsonArray messages = new JsonArray();

                // 使用自定义的系统提示词，替换 {player} 占位符
                String systemPrompt = customSystemPrompt.replace("{player}", playerName);
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", ROLE_SYSTEM);
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
                userMsg.addProperty("role", ROLE_USER);
                userMsg.addProperty("content", enhancedUserMessage);
                messages.add(userMsg);

                requestBody.add("messages", messages);

                Request request = buildRequest(requestBody);

                // ========== 使用流式读取 ==========
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return "§c请求失败：" + response.code() + " - " + response.message();
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        return "§cAPI 响应为空";
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
                                            String content = delta.get("content").getAsString();
                                            if (content == null || content.isEmpty()) {
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
                                        PluginLogger.debug("LLM请求", "JSON 解析失败：" + truncatedData);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        if (e instanceof ConnectionShutdownException) {
                            PluginLogger.debug("LLM请求", "连接被对端关闭");
                        } else {
                            PluginLogger.warn("LLM请求", "读取响应流失败", e);
                        }
                        return "§c读取响应失败：" + e.getMessage();
                    }

                    // 流式输出完成后，调用 showResponse() 触发 completeGeneration()
                    if (responseHandler != null) {
                        responseHandler.showResponse(fullResponse.toString());
                    }

                    return fullResponse.toString();
                }

            } catch (Exception e) {
                PluginLogger.error("LLM请求", "LLM 请求失败", e);
                if (cachedDebugMode) {
                    PluginLogger.debug("LLM请求", "错误详情：" + e.getMessage());
                }
                String errorMsg = "§c请求失败：" + e.getMessage();
                if (responseHandler != null) {
                    responseHandler.handleError(errorMsg);
                }
                return errorMsg;
            } finally {
                if (enableDebugLog && cachedDebugMode) {
                    PluginLogger.debug("LLM请求", "========== API 请求结束 ==========");
                }
            }
        });
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
                PluginLogger.warn("LLM提供商", "HTTP客户端未能在5秒内完全关闭,强制关闭");
                httpClient.dispatcher().executorService().shutdownNow();
            }

            // 清空连接池
            httpClient.connectionPool().evictAll();

            PluginLogger.info("LLM提供商", "HTTP连接池已关闭");
        } catch (Exception e) {
            PluginLogger.error("LLM提供商", "关闭HTTP客户端时发生错误", e);
        }
    }
}
