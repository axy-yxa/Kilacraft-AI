package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;

import java.util.UUID;

/**
 * 静默 AI 响应处理器工厂：供「响应只从 future 拿、不向玩家输出」的后台 LLM 调用使用
 * （对话推荐、触发指令构造等）。
 *
 * <p>GenericLLMProvider 响应完成后会无条件调用 showResponse，若用 PlayerResponseHandler
 * 会把原文发到玩家聊天框——故 showResponse 必须 no-op；getPlayerId 返回真实 UUID 使调用
 * 注册到 inFlightCalls，玩家下线可被取消。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-08
 */
public final class SilentCallHandlerFactory {

    private SilentCallHandlerFactory() {
    }

    /**
     * 构造静默 handler。
     *
     * @param playerId   玩家 UUID（注册 inFlightCalls，下线可取消）
     * @param playerName 玩家名
     * @param logModule  错误日志模块名（如 "对话推荐" / "触发指令"）
     * @return 静默 handler（showResponse no-op，非流式）
     */
    public static AIResponseHandler silent(UUID playerId, String playerName, String logModule) {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return playerId;
            }

            @Override
            public String getPlayerName() {
                return playerName;
            }

            @Override
            public void showResponse(String response) {
                // no-op：响应从 future 拿
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                PluginLoggerUtil.debug(logModule, I18nService.tr("LLM 调用错误: {}", errorMessage));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };
    }
}
