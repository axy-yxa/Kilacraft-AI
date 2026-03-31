package com.zm.kilacraftAI.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.manager.ConversationManager;
import okhttp3.*;

import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * DeepSeekAPI集成
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:22:42
 */
@Deprecated
public class DeepSeekAPI {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final int LOG_TRUNCATE_LENGTH = 50;

    private final KilacraftAI plugin = KilacraftAI.getInstance();
    private final ConfigManager configManager;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public DeepSeekAPI(ConfigManager configManager) {
        this.configManager = configManager;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    /**
     * 构建 HTTP 请求
     */
    private Request buildRequest(JsonObject requestBody) {
        return new Request.Builder()
                .url(configManager.getApiUrl())
                .addHeader("Authorization", "Bearer " + configManager.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
    }

    /**
     * 打印调试日志
     */
    private void printDebugLog(String playerName, String userMessage, Deque<ConversationManager.Message> history) {
        plugin.getLogger().info("[DEBUG] ========== DeepSeek API 请求开始 ==========");
        plugin.getLogger().info("[DEBUG] 玩家：" + playerName);
        plugin.getLogger().info("[DEBUG] 当前消息：" + userMessage);
        plugin.getLogger().info("[DEBUG] 模型：" + configManager.getModel());
        plugin.getLogger().info("[DEBUG] 温度：" + configManager.getTemperature());
        plugin.getLogger().info("[DEBUG] 最大 Token：" + configManager.getMaxTokens());
        
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
        plugin.getLogger().info("[DEBUG] ========== DeepSeek API 请求结束 ==========");
    }

    /**
     * 处理 AI 请求（统一入口）
     * 
     * <p>
     * <strong>推荐使用此方法处理所有 AI 请求</strong>
     * </p>
     * 
     * <p><strong>使用示例：</strong></p>
     * <pre>{@code
     * // 游戏内玩家请求（非流式或流式由配置决定）
     * AIResponseHandler handler = new PlayerResponseHandler(player, message, history);
     * api.processRequest(message, player.getName(), history, handler)
     *     .thenAccept(response -> {
     *         // 可选：处理额外逻辑
     *         saveToHistory(message, response);
     *     });
     * 
     * // 控制台请求（始终使用普通模式）
     * AIResponseHandler handler = new ConsoleResponseHandler(sender);
     * api.processRequest(message, "Console", null, handler);
     * }</pre>
     * 
     * @param userMessage 用户消息
     * @param playerName 玩家名称
     * @param history 历史对话记录
     * @param responseHandler 响应处理器（如果为 null 则使用普通模式，不显示响应）
     * @return 完整的 AI 响应（CompletableFuture 可用于异步处理）
     */
    public CompletableFuture<String> processRequest(String userMessage, String playerName, 
                                                     Deque<ConversationManager.Message> history, 
                                                     AIResponseHandler responseHandler) {
        // 使用默认的系统提示词
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, configManager.getSystemPrompt());
    }

    /**
     * 处理 AI 请求（支持自定义系统提示词）
     * 
     * <p>用于插件命令等需要临时替换人格的场景</p>
     * 
     * @param userMessage 用户消息
     * @param playerName 玩家名称
     * @param history 历史对话记录
     * @param responseHandler 响应处理器
     * @param customSystemPrompt 自定义系统提示词
     * @return 完整的 AI 响应（CompletableFuture 可用于异步处理）
     */
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName,
                                                                           Deque<ConversationManager.Message> history,
                                                                           AIResponseHandler responseHandler,
                                                                           String customSystemPrompt) {
        // 默认启用知识检索增强
        return processRequestWithCustomSystemPrompt(userMessage, playerName, history, responseHandler, customSystemPrompt, true, true);
    }
    
    /**
     * 处理 AI 请求（支持自定义系统提示词和知识检索控制）
     * 
     * <p>用于需要精确控制是否启用知识检索的场景（如 LLM 意图识别不需要知识增强）</p>
     * 
     * @param userMessage 用户消息
     * @param playerName 玩家名称
     * @param history 历史对话记录
     * @param responseHandler 响应处理器
     * @param customSystemPrompt 自定义系统提示词
     * @param enableKnowledgeRetrieval 是否启用知识检索增强
     * @param enableLLMDebugLog 是否显示意图识别Debug日志
     * @return 完整的 AI 响应（CompletableFuture 可用于异步处理）
     */
    public CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName,
                                                                           Deque<ConversationManager.Message> history,
                                                                           AIResponseHandler responseHandler,
                                                                           String customSystemPrompt,
                                                                           boolean enableKnowledgeRetrieval,
                                                                           boolean enableLLMDebugLog) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 打印调试日志
                if (enableLLMDebugLog && configManager.isDebugMode()) {
                    printDebugLog(playerName, userMessage, history);
                }

                // ========== 知识检索增强 ==========
                String enhancedUserMessage = userMessage;
                
                // 检查知识库和知识检索开关
                if (enableKnowledgeRetrieval && configManager.isKnowledgeEnabled()) {
                    KnowledgeRetriever retriever = plugin.getKnowledgeRetriever();
                    
                    if (retriever != null) {
                        // 检索相关知识
                        var relevantKnowledge = retriever.retrieveKnowledge(userMessage);
                        
                        // 如果有相关知识，添加到上下文中
                        if (!relevantKnowledge.isEmpty()) {
                            String knowledgeContext = retriever.formatAsContext(relevantKnowledge);
                            
                            // 增强用户消息（知识 + 原问题）
                            enhancedUserMessage = knowledgeContext + "\n用户问题：" + userMessage;
                            
                            if (configManager.isDebugMode()) {
                                plugin.getLogger().info("[DEBUG] 已检索到 " + relevantKnowledge.size() + " 条相关知识");
                            }
                        }
                    }
                }
                // ===================================

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", configManager.getModel());
                requestBody.addProperty("temperature", configManager.getTemperature());
                requestBody.addProperty("max_tokens", configManager.getMaxTokens());
                // 启用流式响应
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
                userMsg.addProperty("content", enhancedUserMessage);  // 使用增强后的消息
                messages.add(userMsg);

                requestBody.add("messages", messages);

                Request request = buildRequest(requestBody);
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return "§c请求失败：" + response.code() + " - " + response.message();
                    }
                
                    ResponseBody body = response.body();
                    if (body == null) {
                        return "§cAPI 响应为空";
                    }
                
                    StringBuilder fullResponse = new StringBuilder();
                    StringBuilder currentStreamMessage = new StringBuilder();
                    String[] lines = body.string().split("\n");
                
                    for (String line : lines) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data.trim())) {
                                break;
                            }
                
                            try {
                                JsonObject json = gson.fromJson(data, JsonObject.class);
                                JsonArray choices = json.getAsJsonArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JsonObject choice = choices.get(0).getAsJsonObject();
                                    JsonObject delta = choice.getAsJsonObject("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        fullResponse.append(content);
                                        
                                        // 如果是流式模式，回调每个文本片段
                                        if (responseHandler != null && responseHandler.isStreamOutputEnabled()) {
                                            currentStreamMessage.append(content);
                                            responseHandler.showStreamChunk(content, currentStreamMessage.toString());
                                        }
                                    }
                                }
                            } catch (com.google.gson.JsonParseException e) {
                                // 忽略 JSON 解析错误，继续处理下一行
                            }
                        }
                    }
                
                    // 如果不是流式模式，在完成后一次性显示
                    if (responseHandler != null && !responseHandler.isStreamOutputEnabled()) {
                        responseHandler.showResponse(fullResponse.toString());
                    }
                
                    return fullResponse.toString();
                }
    
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "DeepSeekAPI 请求失败", e);
                if (configManager.isDebugMode()) {
                    plugin.getLogger().severe("[DEBUG] 错误详情：" + e.getMessage());
                }
                String errorMsg = "§c请求失败：" + e.getMessage();
                if (responseHandler != null) {
                    responseHandler.handleError(errorMsg);
                }
                return errorMsg;
            }
        });
    }
}