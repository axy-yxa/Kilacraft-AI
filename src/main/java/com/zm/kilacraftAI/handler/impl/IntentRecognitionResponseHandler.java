package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.handler.BaseResponseHandler;

import java.util.UUID;

/**
 * 意图识别 AI 响应处理器
 * 
 * <p>专用于技能意图识别场景，不显示任何响应给玩家</p>
 * <p>所有日志输出到控制台用于调试</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class IntentRecognitionResponseHandler extends BaseResponseHandler {
    
    @Override
    public UUID getPlayerId() {
        return null; // 意图识别不需要玩家 ID
    }
    
    @Override
    public String getPlayerName() {
        return "IntentRecognizer";
    }
    
    @Override
    protected void sendMessage(String message) {
        // 意图识别场景下，只记录日志，不向玩家显示任何内容
        if (plugin != null && plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] [意图识别结果] \n" + message);
        }
    }

    @Override
    public boolean isStreamOutputEnabled() {
        return false;
    }
}
