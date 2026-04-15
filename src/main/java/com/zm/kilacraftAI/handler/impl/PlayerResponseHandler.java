package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.enums.OutputScenario;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 玩家 AI 响应处理器（游戏内使用）
 *
 * <p>直接使用 AIResponsePipeline 统一输出，支持配置化载体</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class PlayerResponseHandler implements AIResponseHandler {
    
    private final KilacraftAI plugin;
    private final Player player;
    
    public PlayerResponseHandler(Player player) {
        this.plugin = KilacraftAI.getInstance();
        this.player = player;
        
        // 如果启用流式输出，立即显示占位符（窗口期管理）
        if (plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()) {
            plugin.getStreamOutputManager().startGeneration(player);
        }
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
    public void showResponse(String response) {
        // 流式模式：完成生成并输出最终结果
        if (plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()) {
            plugin.getStreamOutputManager().completeGeneration(player, response);
        } else {
            // 非流式模式：使用统一响应管线（普通对话场景）
            plugin.getResponsePipeline().send(player, response, OutputScenario.NORMAL_CHAT);
        }
    }
    
    @Override
    public void showStreamChunk(String chunk, String currentMessage) {
        // 流式模式下每收到一个片段就显示（通过 StreamOutputManager 更新）
        plugin.getStreamOutputManager().updateStreamChunk(player, chunk, currentMessage);
    }

    @Override
    public void handleError(String errorMessage) {
        // 取消流式生成状态
        if (plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()) {
            plugin.getStreamOutputManager().cancelGeneration(player);
        }
        // 错误消息使用 ERROR 场景配置
        plugin.getResponsePipeline().sendError(player, errorMessage);
    }

    @Override
    public boolean isStreamOutputEnabled() {
        // 使用 output.stream.enabled 配置
        return plugin.getConfigManager().getOutputConfigManager().isStreamEnabled();
    }
}
