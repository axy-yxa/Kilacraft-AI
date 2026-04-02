package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.handler.BaseResponseHandler;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * 控制台 AI 响应处理器（后台使用）
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class ConsoleResponseHandler extends BaseResponseHandler {
    
    private final CommandSender sender;

    public ConsoleResponseHandler(CommandSender sender) {
        this.sender = sender;
    }
    
    @Override
    public UUID getPlayerId() {
        // 控制台专用 UUID
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
    
    @Override
    public String getPlayerName() {
        return "Console";
    }
    
    @Override
    protected void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public boolean isStreamOutputEnabled() {
        // 控制台模式不支持流式输出（可以，但是体验不好，会刷屏）
        return false;
    }
}
