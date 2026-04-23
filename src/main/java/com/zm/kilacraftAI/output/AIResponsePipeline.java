package com.zm.kilacraftAI.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.manager.SoundEffectManager;
import com.zm.kilacraftAI.manager.StreamOutputManager;
import com.zm.kilacraftAI.util.MessageUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.entity.Player;

/**
 * AI 响应输出管线
 * 统一所有 AI 回复的输出入口
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class AIResponsePipeline {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;
    private final StreamOutputManager streamOutputManager;
    private final SoundEffectManager soundEffectManager;
    /**
     * 消息分发器
     */
    @Getter
    private final MessageDispatcher dispatcher;

    public AIResponsePipeline(KilacraftAI plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.dispatcher = new MessageDispatcher(plugin);
        this.streamOutputManager = plugin.getStreamOutputManager();
        this.soundEffectManager = plugin.getSoundEffectManager();
    }

    /**
     * 发送 AI 响应到玩家（统一入口）
     *
     * @param player     目标玩家
     * @param rawMessage 原始消息（未格式化，可能包含 Markdown）
     * @param scenario   输出场景（用于场景级载体配置）
     */
    public void send(Player player, String rawMessage, OutputScenario scenario) {
        if (player == null || rawMessage == null) {
            return;
        }

        // 统一打印DEBUG日志（所有场景的最终回复都从这里输出）
        PluginLogger.debug("AI响应", player.getName() + " 收到AI回复 [" + scenario.name() + "]：" + rawMessage);

        // 播放 AI 回复音效（与输出同步）
        if (soundEffectManager != null) {
            soundEffectManager.playResponseSound(player);
        }

        // 格式化
        String formatted = formatMessage(rawMessage, true);

        // 路由（选择载体）
        OutputChannel channel = resolveChannel(scenario);

        // 输出
        dispatcher.dispatch(player, formatted, channel);
    }

    /**
     * 发送错误消息到玩家
     *
     * @param player       目标玩家
     * @param errorMessage 错误消息（已格式化）
     */
    public void sendError(Player player, String errorMessage) {
        if (player == null || errorMessage == null) {
            return;
        }

        // 错误消息使用 ERROR 场景配置
        OutputChannel channel = resolveChannel(OutputScenario.ERROR);
        dispatcher.dispatch(player, errorMessage, channel);
    }

    /**
     * 发送思考消息到玩家
     *
     * <p>思考消息的载体由 config.yml 中的 thinking_channel 配置决定，</p>
     * <p>不使用场景级配置，而是直接使用指定的载体。</p>
     *
     * @param player  目标玩家
     * @param message 思考消息（已格式化）
     * @param channel 输出载体（由调用方传入配置值）
     */
    public void sendThinking(Player player, String message, OutputChannel channel) {
        if (player == null || message == null) {
            return;
        }
        dispatcher.dispatch(player, message, channel);
    }

    /**
     * 启动流式输出（显示"生成中..."占位符）
     *
     * <p>在 LLM 请求开始时调用，显示占位符并设置 GENERATING 状态</p>
     *
     * @param player  目标玩家
     * @param channel 输出载体（由调用方传入场景配置）
     */
    public void startStream(Player player, OutputChannel channel) {
        startStream(player, channel, false);
    }

    /**
     * 启动流式输出
     *
     * @param player  目标玩家
     * @param channel 输出载体（由调用方传入场景配置）
     * @param silent  是否静默启动（不显示占位符，仅初始化状态机）
     */
    public void startStream(Player player, OutputChannel channel, boolean silent) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }
        streamOutputManager.startGeneration(player, channel, silent);
    }

    /**
     * 更新流式输出内容
     *
     * <p>LLM 流式响应中，每收到一个 chunk 调用一次</p>
     *
     * @param player         目标玩家
     * @param chunk          当前片段
     * @param currentMessage 累积的完整消息
     * @param channel        输出载体（由调用方传入场景配置）
     */
    public void updateStream(Player player, String chunk, String currentMessage, OutputChannel channel) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }
        streamOutputManager.updateStreamChunk(player, chunk, currentMessage, channel);
    }

    /**
     * 完成流式输出
     *
     * <p>LLM 响应完成后调用，设置 COMPLETED 状态并发送最终消息</p>
     * <p>流式输出完成时，最终载体更新统一由 StreamOutputManager 在主线程执行，</p>
     * <p>不通过 send() 二次操作载体，避免竞态覆盖和闪烁</p>
     *
     * @param player       目标玩家
     * @param finalMessage 最终完整消息（原始文本，未格式化）
     * @param scenario     输出场景（用于场景级载体配置）
     */
    public void completeStream(Player player, String finalMessage, OutputScenario scenario) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        // 路由（选择载体）
        OutputChannel channel = resolveChannel(scenario);

        // 格式化（带前缀）
        String formatted = formatMessage(finalMessage, true);

        // 完成流式状态机，同时将载体更新为带前缀的最终消息
        // 所有载体操作统一在 StreamOutputManager 内部主线程执行，只操作一次
        streamOutputManager.completeGeneration(player, formatted, channel);

        // 非输出副作用（由管线统一管理）
        PluginLogger.debug("AI响应", "{} 收到AI回复 [{}]：{}", player.getName(), scenario.name(), finalMessage);
        if (soundEffectManager != null) {
            soundEffectManager.playResponseSound(player);
        }
    }

    /**
     * 取消流式输出（异常场景）
     *
     * @param player 目标玩家
     */
    public void cancelStream(Player player) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }
        streamOutputManager.cancelGeneration(player);
    }

    /**
     * 获取场景对应的输出载体
     *
     * @param scenario 输出场景
     * @return 载体类型
     */
    public OutputChannel getChannelForScenario(OutputScenario scenario) {
        return config.getChannelForScenario(scenario);
    }

    /**
     * 公屏广播（关键词触发 public_reply=true 时使用）
     *
     * <p>强制使用 CHAT 载体，因为其他载体（BOSSBAR/SIDEBAR/TITLE/ACTIONBAR）都是玩家私有的，会互相覆盖</p>
     *
     * @param rawMessage 原始消息
     * @param excludePlayer 要排除的玩家（触发者，当场景载体为CHAT时需要排除避免重复）
     */
    public void broadcast(String rawMessage, Player excludePlayer) {
        if (rawMessage == null) {
            return;
        }

        // 格式化
        String formatted = formatMessage(rawMessage, true);

        // 强制使用 CHAT 载体（公屏广播必须用 CHAT）
        OutputChannel channel = OutputChannel.CHAT;

        // 输出到所有在线玩家
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            // 如果场景载体是 CHAT，跳过触发者避免重复输出
            if (onlinePlayer.equals(excludePlayer)) {
                continue;
            }
            dispatcher.dispatch(onlinePlayer, formatted, channel);
        }
    }

    /**
     * 格式化消息
     *
     * @param rawMessage  原始消息
     * @param applyPrefix 是否应用 AI 前缀
     * @return 格式化后的 Minecraft 消息
     */
    private String formatMessage(String rawMessage, boolean applyPrefix) {
        String result = MessageUtil.convertMarkdownToMinecraft(rawMessage);
        if (applyPrefix) {
            result = MessageUtil.getAIPrefix() + result;
        }
        return result;
    }

    /**
     * 解析输出载体
     *
     * <p>路由逻辑：</p>
     * <ol>
     *   <li>优先使用场景级配置（scenarios.xxx）</li>
     *   <li>场景未配置时回退到全局默认（default_channel）</li>
     * </ol>
     *
     * @param scenario 输出场景
     * @return 输出载体
     */
    private OutputChannel resolveChannel(OutputScenario scenario) {
        return config.getChannelForScenario(scenario);
    }

    /**
     * 清理资源（插件卸载时调用）
     */
    public void cleanup() {
        dispatcher.getBossBarManager().cleanup();
        dispatcher.getScoreboardManager().cleanup();
    }
}
