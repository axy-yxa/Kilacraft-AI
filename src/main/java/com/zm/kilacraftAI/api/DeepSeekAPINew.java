package com.zm.kilacraftAI.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.manager.ConversationManager;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * DeepSeekAPI 集成（性能优化版）
 *
 * @author Zm_Mmm
 * @since 2026-03-31 13:34:23
 */
public class DeepSeekAPINew {

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

    // 缓存配置值，减少重复获取开销
    // 注意：配置缓存会在 ConfigManager.reloadConfig() 时通过 refreshConfigCache() 手动刷新
    private volatile String cachedModel;
    private volatile Double cachedTemperature;
    private volatile Integer cachedMaxTokens;
    private volatile Boolean cachedDebugMode;
    private volatile Boolean cachedKnowledgeEnabled;

    public DeepSeekAPINew(ConfigManager configManager) {
        this.configManager = configManager;

        // 优化 配置连接池，复用 HTTP 连接
        ConnectionPool connectionPool = new ConnectionPool(10, 5, TimeUnit.MINUTES);
        // 自动重试失败的连接
        this.httpClient = new OkHttpClient.Builder().connectionPool(connectionPool).connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
        this.gson = new Gson();

        // 初始化配置缓存
        refreshConfigCache();

        // 输出 HTTP 连接池配置日志
        plugin.getLogger().info("[HTTP] 已启用连接池优化：最大空闲连接数=10, 保持时间=5 分钟");
        plugin.getLogger().info("[HTTP] 超时配置：连接=30s, 读取=60s, 写入=30s");
        plugin.getLogger().info("[HTTP] 已启用自动重试失败连接");
    }

    /**
     * 刷新配置缓存（由 ConfigManager.reloadConfig() 调用）
     */
    public void refreshConfigCache() {
        this.cachedModel = configManager.getModel();
        this.cachedTemperature = configManager.getTemperature();
        this.cachedMaxTokens = configManager.getMaxTokens();
        this.cachedDebugMode = configManager.isDebugMode();
        this.cachedKnowledgeEnabled = configManager.isKnowledgeEnabled();
    }

    /**
     * 构建 HTTP 请求（优化：复用 Request.Builder）
     */
    private Request buildRequest(JsonObject requestBody) {
        // 启用压缩
        return new Request.Builder().url(configManager.getApiUrl()).addHeader("Authorization", "Bearer " + configManager.getApiKey()).addHeader("Content-Type", "application/json").addHeader("Accept-Encoding", "gzip").post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json"))).build();
    }

    /**
     * 打印调试日志（优化：减少字符串拼接）
     */
    private void printDebugLog(String playerName, String userMessage, Deque<ConversationManager.Message> history) {
        plugin.getLogger().info("[DEBUG] ========== DeepSeek API 请求开始 ==========");
        plugin.getLogger().info("[DEBUG] 玩家：" + playerName);
        plugin.getLogger().info("[DEBUG] 当前消息：" + userMessage);
        plugin.getLogger().info("[DEBUG] 模型：" + cachedModel);
        plugin.getLogger().info("[DEBUG] 温度：" + cachedTemperature);
        plugin.getLogger().info("[DEBUG] 最大 Token:" + cachedMaxTokens);

        // 打印历史记录信息
        if (history != null && !history.isEmpty()) {
            plugin.getLogger().info("[DEBUG] 历史对话数量：" + history.size() + " 条");
            int index = 0;
            for (ConversationManager.Message msg : history) {
                index++;
                String content = msg.getContent();
                // AI 回答的历史记录只打印前若干个字符，避免日志过长
                if (ROLE_ASSISTANT.equals(msg.getRole()) && content.length() > LOG_TRUNCATE_LENGTH) {
                    content = content.substring(0, LOG_TRUNCATE_LENGTH) + "... (共" + msg.getContent().length() + " 字符)";
                }
                plugin.getLogger().info("[DEBUG] 历史 [" + index + "] (" + msg.getRole() + ")：" + content);
            }
        } else {
            plugin.getLogger().info("[DEBUG] 历史对话：无");
        }
    }

    /**
     * 处理 AI 请求（统一入口）
     *
     * <p>
     * <strong>推荐使用此方法处理所有 AI 请求</strong>
     * </p>
     *
     * @param userMessage     用户消息
     * @param playerName      玩家名称
     * @param history         历史对话记录
     * @param responseHandler 响应处理器（如果为 null 则使用普通模式，不显示响应）
     * @return 完整的 AI 响应（CompletableFuture 可用于异步处理）
     */
    public CompletableFuture<String> processRequest(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler) {
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, configManager.getSystemPrompt());
    }

    /**
     * 处理 AI 请求（支持自定义系统提示词）
     */
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt) {
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, true, true);
    }

    /**
     * 处理 AI 请求（支持自定义系统提示词和知识检索控制）
     *
     * <p>性能优化版：
     * <ul>
     *   <li>使用连接池复用 HTTP 连接</li>
     *   <li>流式读取响应，降低首字延迟</li>
     *   <li>配置缓存减少重复获取</li>
     *   <li>预分配 StringBuilder 容量</li>
     *   <li>优化知识检索结果格式化</li>
     * </ul>
     * </p>
     */
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 打印调试日志
                if (enableDebugLog && cachedDebugMode) {
                    printDebugLog(playerName, userMessage, history);
                }

                // ========== 知识检索增强（优化：使用 StringBuilder 减少拼接）==========
                String enhancedUserMessage = userMessage;

                // 检查知识库和知识检索开关
                if (enableKnowledgeRetrieval && cachedKnowledgeEnabled) {
                    KnowledgeRetriever retriever = plugin.getKnowledgeRetriever();

                    if (retriever != null) {
                        // 检索相关知识
                        var relevantKnowledge = retriever.retrieveKnowledge(userMessage);

                        // 如果有相关知识，添加到上下文中
                        if (!relevantKnowledge.isEmpty()) {
                            String knowledgeContext = retriever.formatAsContext(relevantKnowledge);

                            // 优化：使用 StringBuilder 减少字符串拼接开销
                            StringBuilder enhancedBuilder = new StringBuilder(knowledgeContext.length() + userMessage.length() + 20);
                            enhancedBuilder.append(knowledgeContext).append("\n用户问题：").append(userMessage);
                            enhancedUserMessage = enhancedBuilder.toString();

                            if (cachedDebugMode) {
                                plugin.getLogger().info("[DEBUG] 已添加 " + relevantKnowledge.size() + " 个知识片段到上下文中");
                            }
                        }
                    }
                }
                // ===================================

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", cachedModel);
                requestBody.addProperty("temperature", cachedTemperature);
                requestBody.addProperty("max_tokens", cachedMaxTokens);

                // 始终使用流式请求（性能更优），根据 handler 决定是否实时显示
                requestBody.addProperty("stream", true);

                JsonArray messages = new JsonArray();

                // 使用自定义的系统提示词，替换 {player} 占位符
                String systemPrompt = customSystemPrompt.replace("{player}", playerName);
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", ROLE_SYSTEM);
                systemMessage.addProperty("content", systemPrompt);
                messages.add(systemMessage);

                // 添加历史对话记录（优化：批量添加，减少重复代码）
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

                // ========== 核心优化：使用流式读取 ==========
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return "§c请求失败：" + response.code() + " - " + response.message();
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        return "§cAPI 响应为空";
                    }

                    // 优化：预分配容量，减少扩容开销
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

                                // 优化：使用更精确的异常处理，只包裹解析部分
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
                                        plugin.getLogger().warning("[DEBUG] JSON 解析失败：" + truncatedData);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "读取响应流失败", e);
                        return "§c读取响应失败：" + e.getMessage();
                    }

                    // 如果 handler 未开启流式输出，完成后一次性显示
                    if (responseHandler != null && !responseHandler.isStreamOutputEnabled()) {
                        responseHandler.showResponse(fullResponse.toString());
                    }

                    return fullResponse.toString();
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "DeepSeekAPI 请求失败", e);
                if (cachedDebugMode) {
                    plugin.getLogger().severe("[DEBUG] 错误详情：" + e.getMessage());
                }
                String errorMsg = "§c请求失败：" + e.getMessage();
                if (responseHandler != null) {
                    responseHandler.handleError(errorMsg);
                }
                return errorMsg;
            } finally {
                if (enableDebugLog && cachedDebugMode) {
                    plugin.getLogger().info("[DEBUG] ========== DeepSeek API 请求结束 ==========");
                }
            }
        });
    }

    /**
     * 关闭 HTTP 客户端连接池（用于插件卸载时）
     */
    public void shutdown() {
        try {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            plugin.getLogger().info("[HTTP] 连接池已关闭");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "关闭 HTTP 客户端时发生错误", e);
        }
    }
}
