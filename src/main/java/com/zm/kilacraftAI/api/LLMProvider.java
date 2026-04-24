package com.zm.kilacraftAI.api;

import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;

import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 提供商统一接口
 * <p>
 * 支持多个 LLM 提供商的统一接入和无缝切换
 *
 * @author Zm_Mmm
 * @since 2026-04-03
 */
public interface LLMProvider {

    /**
     * 处理 AI 请求（统一入口）
     *
     * @param userMessage     用户消息
     * @param playerName      玩家名称
     * @param history         历史对话记录
     * @param responseHandler 响应处理器
     * @return 完整的 AI 响应
     */
    CompletableFuture<String> processRequest(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler);

    /**
     * 处理 AI 请求（支持自定义系统提示词）
     *
     * @param userMessage        用户消息
     * @param playerName         玩家名称
     * @param history            历史对话记录
     * @param responseHandler    响应处理器
     * @param customSystemPrompt 自定义系统提示词
     * @return 完整的 AI 响应
     */
    CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt);

    /**
     * 处理 AI 请求（支持自定义系统提示词和知识检索控制）
     *
     * @param userMessage              用户消息
     * @param playerName               玩家名称
     * @param history                  历史对话记录
     * @param responseHandler          响应处理器
     * @param customSystemPrompt       自定义系统提示词
     * @param enableKnowledgeRetrieval 是否启用知识检索
     * @param enableDebugLog           是否启用调试日志
     * @return 完整的 AI 响应
     */
    CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog);

    /**
     * 处理 AI 请求（支持自定义系统提示词、知识检索控制和 JSON 输出）
     *
     * @param userMessage              用户消息
     * @param playerName               玩家名称
     * @param history                  历史对话记录
     * @param responseHandler          响应处理器
     * @param customSystemPrompt       自定义系统提示词
     * @param enableKnowledgeRetrieval 是否启用知识检索
     * @param enableDebugLog           是否启用调试日志
     * @param enableJsonOutput         是否启用 JSON 输出
     * @return 完整的 AI 响应
     */
    CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, String playerName, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String customSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog, boolean enableJsonOutput);

    /**
     * 刷新配置缓存
     */
    void refreshConfigCache();

    /**
     * 关闭资源（用于插件卸载时）
     */
    void shutdown();
}
