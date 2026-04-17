package com.zm.kilacraftAI.handler;

import java.util.UUID;

/**
 * AI 响应处理器接口
 * 用于统一处理 AI 的响应结果
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public interface AIResponseHandler {

    /**
     * 获取玩家 UUID（如果适用）
     *
     * @return 玩家 UUID，非玩家场景返回 null
     */
    UUID getPlayerId();

    /**
     * 获取玩家名称
     *
     * @return 玩家名称或标识符
     */
    String getPlayerName();

    /**
     * 显示 AI 响应（普通模式）
     *
     * @param response 完整的 AI 响应
     */
    void showResponse(String response);

    /**
     * 显示流式响应的文本片段
     *
     * @param chunk          文本片段
     * @param currentMessage 当前累积的完整消息
     */
    void showStreamChunk(String chunk, String currentMessage);

    /**
     * 处理错误
     *
     * @param errorMessage 错误信息
     */
    void handleError(String errorMessage);

    /**
     * 是否启用流式输出
     *
     * @return true 启用流式输出
     */
    boolean isStreamOutputEnabled();
}
