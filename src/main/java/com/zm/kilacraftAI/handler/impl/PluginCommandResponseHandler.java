package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * 插件命令 AI 响应处理器
 *
 * <p>用于处理第三方插件通过控制台执行的 AI 请求</p>
 * <p>支持指定玩家 UUID 和人格</p>
 * <p>仅在 debug 模式下输出到控制台，实际回复通过回调命令返回</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-26
 */
public class PluginCommandResponseHandler implements AIResponseHandler {

    private final KilacraftAI plugin;
    private final CommandSender sender;
    private final UUID targetPlayerId;
    private final String targetPlayerName;

    public PluginCommandResponseHandler(CommandSender sender, UUID targetPlayerId, String targetPlayerName) {
        this.plugin = KilacraftAI.getInstance();
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
    public void showResponse(String message) {
        // 插件命令模式：仅在 debug 模式下输出到控制台
        // 实际回复通过回调命令返回给第三方插件
        if (plugin.getConfigManager().isDebugMode()) {
            sender.sendMessage(message);
        }
    }

    @Override
    public void showStreamChunk(String chunk, String currentMessage) {
        // 插件命令模式不支持流式输出
    }

    @Override
    public void handleError(String errorMessage) {
        if (plugin.getConfigManager().isDebugMode()) {
            sender.sendMessage(errorMessage);
        }
    }

    @Override
    public boolean isStreamOutputEnabled() {
        // 插件命令模式不支持流式输出
        return false;
    }
}
