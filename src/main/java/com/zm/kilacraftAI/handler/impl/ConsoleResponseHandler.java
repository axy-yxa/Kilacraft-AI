package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.handler.AIResponseHandler;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * 控制台 AI 响应处理器（后台使用）
 *
 * <p>直接输出到控制台，不使用 Pipeline</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class ConsoleResponseHandler implements AIResponseHandler {
    
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
    public void showResponse(String message) {
        sender.sendMessage(message);
    }

    @Override
    public void showStreamChunk(String chunk, String currentMessage) {
        // 控制台不支持流式输出
    }

    @Override
    public void handleError(String errorMessage) {
        sender.sendMessage(errorMessage);
    }

    @Override
    public boolean isStreamOutputEnabled() {
        // 控制台不支持流式输出
        return false;
    }
}
