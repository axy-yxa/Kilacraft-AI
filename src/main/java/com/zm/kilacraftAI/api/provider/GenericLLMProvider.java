package com.zm.kilacraftAI.api.provider;

import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.ChineseTextUtil;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import okhttp3.*;
import okhttp3.internal.http2.ConnectionShutdownException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
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
        plugin.getLogger().info("初始化HTTP连接池");
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
     * <p>策略：以 analysis_prompt_suffix 为边界，只从业务内容中提取关键词，排除提示词干扰</p>
     * 
     * @param userMessage 完整的提示词（包含历史、当前输入、执行结果）
     * @return 优化后的搜索查询（空格分隔的关键词）
     */
    private String extractSearchQuery(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return userMessage;
        }

        // analysis_prompt_suffix 作为边界，只提取业务内容
        String suffix = plugin.getConfigManager().getAgentAnalysisPromptSuffix();
        String businessContent = userMessage;
        
        if (suffix != null && !suffix.isEmpty()) {
            int suffixIndex = userMessage.indexOf(suffix);
            if (suffixIndex > 0) {
                // 只取 suffix 之前的业务内容
                businessContent = userMessage.substring(0, suffixIndex);
            }
        }

        return ChineseTextUtil.toSearchQuery(businessContent);
    }

    /**
     * 打印调试日志（优化：减少字符串拼接）
     */
    private void printDebugLog(String playerName, String userMessage, Deque<ConversationManager.Message> history) {
        plugin.getLogger().info("[DEBUG] ========== API 请求开始 ==========");
        plugin.getLogger().info("[DEBUG] 玩家：" + playerName);
        plugin.getLogger().info("[DEBUG] 当前消息：" + userMessage);
        plugin.getLogger().info("[DEBUG] 模型：" + cachedModel);
        plugin.getLogger().info("[DEBUG] URL：" + cachedApiUrl);
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
                requestBody.addProperty("stream", true); // 始终使用流式请求

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
                                        plugin.getLogger().warning("[DEBUG] JSON 解析失败：" + truncatedData);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        if (e instanceof ConnectionShutdownException) {
                            plugin.getLogger().warning("[DEBUG] 连接被对端关闭");
                        } else {
                            plugin.getLogger().log(Level.WARNING, "读取响应流失败", e);
                        }
                        return "§c读取响应失败：" + e.getMessage();
                    }

                    // 如果 handler 未开启流式输出，完成后一次性显示
                    if (responseHandler != null && !responseHandler.isStreamOutputEnabled()) {
                        responseHandler.showResponse(fullResponse.toString());
                    }

                    return fullResponse.toString();
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "LLM 请求失败", e);
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
                    plugin.getLogger().info("[DEBUG] ========== API 请求结束 ==========");
                }
            }
        });
    }

    @Override
    public void shutdown() {
        try {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            plugin.getLogger().info("HTTP连接池已关闭");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "关闭 HTTP 客户端时发生错误", e);
        }
    }
}
