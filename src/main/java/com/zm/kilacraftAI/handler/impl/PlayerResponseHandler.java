package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.handler.BaseResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.UUID;

/**
 * 玩家 AI 响应处理器（游戏内使用）
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class PlayerResponseHandler extends BaseResponseHandler {
    
    private final Player player;
    
    public PlayerResponseHandler(Player player, String userMessage, Deque<ConversationManager.Message> history) {
        super();
        this.player = player;
    }
    
    @Override
    public UUID getPlayerId() {
        return player.getUniqueId();
    }
    
    @Override
    public String getPlayerName() {
        return player.getName();
    }
    
    @Override
    protected void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public boolean isStreamOutputEnabled() {
        // 命令模式不支持流式输出（可以，但是体验不好，会刷屏）
        return false;
    }
}
