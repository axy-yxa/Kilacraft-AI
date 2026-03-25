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
        return null; // 控制台没有 UUID
    }
    
    @Override
    public String getPlayerName() {
        return "Console";
    }
    
    @Override
    protected void sendMessage(String message) {
        sender.sendMessage(message);
    }
}
