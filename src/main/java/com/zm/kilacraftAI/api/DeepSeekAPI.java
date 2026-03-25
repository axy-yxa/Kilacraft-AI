package com.zm.kilacraftAI.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
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
    private void printDebugLog(String playerName, String userMessage, Deque<ChatListener.Message> history) {
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
            for (ChatListener.Message msg : history) {
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

    public CompletableFuture<String> sendMessage(String userMessage, String playerName) {
        return sendMessage(userMessage, playerName, null);
    }
    
    public CompletableFuture<String> sendMessage(String userMessage, String playerName, Deque<ChatListener.Message> history) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 打印调试日志
                if (configManager.isDebugMode()) {
                    printDebugLog(playerName, userMessage, history);
                }

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", configManager.getModel());
                requestBody.addProperty("temperature", configManager.getTemperature());
                requestBody.addProperty("max_tokens", configManager.getMaxTokens());
                // 启用流式响应
                requestBody.addProperty("stream", true);

                JsonArray messages = new JsonArray();

                // 从配置读取系统提示词，替换 {player} 占位符
                String systemPrompt = configManager.getSystemPrompt().replace("{player}", playerName);
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", ROLE_SYSTEM);
                systemMessage.addProperty("content", systemPrompt);
                messages.add(systemMessage);
                
                // 添加历史对话记录
                if (history != null && !history.isEmpty()) {
                    for (ChatListener.Message msg : history) {
                        JsonObject msgObj = new JsonObject();
                        msgObj.addProperty("role", msg.getRole());
                        msgObj.addProperty("content", msg.getContent());
                        messages.add(msgObj);
                    }
                }

                // 用户消息
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", ROLE_USER);
                userMsg.addProperty("content", userMessage);
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
                                    }
                                }
                            } catch (com.google.gson.JsonParseException e) {
                                // 忽略 JSON 解析错误，继续处理下一行
                            }
                        }
                    }
                
                    return fullResponse.toString();
                }
    
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "DeepSeekAPI 请求失败", e);
                if (configManager.isDebugMode()) {
                    plugin.getLogger().severe("[DEBUG] 错误详情：" + e.getMessage());
                }
                return "§c请求失败：" + e.getMessage();
            }
        });
    }
}