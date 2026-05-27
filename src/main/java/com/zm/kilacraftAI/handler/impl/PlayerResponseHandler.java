package com.zm.kilacraftAI.handler.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.handler.AIResponseHandler;
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
    private final OutputScenarioEnum scenario;
    private final java.util.function.Consumer<String> customSendResponse;  // 自定义发送逻辑（如公屏广播）

    public PlayerResponseHandler(Player player, OutputScenarioEnum scenario) {
        this(KilacraftAI.getInstance(), player, scenario, null);
    }

    public PlayerResponseHandler(KilacraftAI plugin, Player player, OutputScenarioEnum scenario, java.util.function.Consumer<String> customSendResponse) {
        this.plugin = plugin;
        this.player = player;
        this.scenario = scenario;
        this.customSendResponse = customSendResponse;

        // 注意：流式占位符已在 AIRequestHandler.handleNormalAIRequest() 中提前显示
        // 这里不再调用 startGeneration()，避免重复显示
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
        // 如果有自定义发送逻辑（如公屏广播），使用它
        if (customSendResponse != null) {
            customSendResponse.accept(response);
            return;
        }

        // 默认逻辑：流式模式：通过管线完成流式输出
        if (plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()) {
            plugin.getResponsePipeline().completeStream(player, response, scenario);
        } else {
            // 非流式模式：使用统一响应管线（普通对话场景）
            plugin.getResponsePipeline().send(player, response, scenario);
        }
    }

    @Override
    public void showStreamChunk(String chunk, String currentMessage) {
        OutputChannelEnum channel = plugin.getResponsePipeline().getChannelForScenario(scenario);
        plugin.getResponsePipeline().updateStream(player, chunk, currentMessage, channel);
    }

    @Override
    public void handleError(String errorMessage) {
        // 取消流式生成状态（通过管线）
        if (plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()) {
            plugin.getResponsePipeline().cancelStream(player);
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
