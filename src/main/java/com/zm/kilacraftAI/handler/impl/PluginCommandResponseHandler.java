package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.handler.BaseResponseHandler;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * 插件命令 AI 响应处理器
 *
 * <p>用于处理第三方插件通过控制台执行的 AI 请求</p>
 * <p>支持指定玩家 UUID 和人格，响应发送给控制台</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-26
 */
public class PluginCommandResponseHandler extends BaseResponseHandler {

    private final CommandSender sender;
    private final UUID targetPlayerId;
    private final String targetPlayerName;

    public PluginCommandResponseHandler(CommandSender sender, UUID targetPlayerId, String targetPlayerName) {
        this.sender = sender;
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
    }

    @Override
    public UUID getPlayerId() {
        return targetPlayerId;
    }

    @Override
    public String getPlayerName() {
        return targetPlayerName != null ? targetPlayerName : "Unknown";
    }

    @Override
    protected void sendMessage(String message) {
        // 发送给控制台（或其他命令执行者）
        sender.sendMessage(message);
    }
}
