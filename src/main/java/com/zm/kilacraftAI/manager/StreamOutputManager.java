package com.zm.kilacraftAI.manager;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.output.MessageDispatcher;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式输出管理器
 *
 * <p>管理流式输出的状态机和占位符窗口期</p>
 * <p>核心功能：</p>
 * <ul>
 *   <li>窗口期管理：AI 请求发起后到首个片段前的"生成中..."状态</li>
 *   <li>流式状态追踪：每个玩家的流式生成状态（IDLE/GENERATING/COMPLETED）</li>
 *   <li>载体路由：根据配置将流式内容输出到 ACTION_BAR/BOSS_BAR</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class StreamOutputManager {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;
    private final MessageDispatcher dispatcher;

    /**
     * 流式生成状态
     */
    public enum GenerationState {
        /** 空闲（无流式生成） */
        IDLE,
        /** 生成中（窗口期 + 流式片段接收中） */
        GENERATING,
        /** 已完成（流式生成结束，等待清理） */
        COMPLETED
    }

    /**
     * 玩家流式状态追踪
     * Key: Player UUID, Value: 当前生成状态
     */
    private final Map<UUID, GenerationState> playerStates = new ConcurrentHashMap<>();

    public StreamOutputManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.dispatcher = new MessageDispatcher(plugin);
    }

    /**
     * 获取占位符消息（从 MessageUtil 动态读取配置）
     *
     * @return 完整的占位符消息（带前缀）
     */
    private String getPlaceholderMessage() {
        // 使用 MessageUtil 的 thinking_message 配置，保持统一
        return MessageUtil.getFullThinkingMessage();
    }

    /**
     * 开始流式生成（窗口期）
     *
     * <p>在 AI 请求发起时调用，立即显示“生成中...”占位符</p>
     *
     * @param player 目标玩家
     */
    public void startGeneration(Player player) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String placeholder = getPlaceholderMessage();
        
        // 设置状态为 GENERATING
        playerStates.put(playerId, GenerationState.GENERATING);

        // 立即显示占位符
        OutputChannel channel = config.getStreamChannel();
        dispatchToChannel(player, placeholder, channel);
    }

    /**
     * 更新流式内容（接收片段）
     *
     * <p>每收到一个 LLM 片段时调用，更新显示内容</p>
     * <p>状态检查：只在 GENERATING 状态下接受更新，防止竞态条件</p>
     *
     * @param player        目标玩家
     * @param chunk         新片段（当前未使用，保留用于未来日志/统计扩展）
     * @param fullMessage   当前累积的完整消息（用于显示）
     */
    public void updateStreamChunk(Player player, String chunk, String fullMessage) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        GenerationState state = playerStates.get(playerId);

        // 状态检查：只在 GENERATING 状态下接受更新
        // 防止：1) 未开始就收到片段  2) 完成后还收到残留片段
        if (state != GenerationState.GENERATING) {
            return;
        }

        // 更新显示（使用流式载体）
        OutputChannel channel = config.getStreamChannel();
        dispatchToChannel(player, fullMessage, channel);
    }

    /**
     * 完成流式生成
     *
     * <p>LLM 响应完成后调用，设置 COMPLETED 状态并根据配置保留最终结果</p>
     *
     * @param player        目标玩家
     * @param finalMessage  最终完整消息
     */
    public void completeGeneration(Player player, String finalMessage) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        
        // 设置状态为 COMPLETED，拒绝后续的 updateStreamChunk
        playerStates.put(playerId, GenerationState.COMPLETED);

        // 根据配置决定是否保留最终结果到默认载体
        if (config.isStreamKeepFinalInDefault()) {
            OutputChannel defaultChannel = config.getDefaultChannel();
            OutputChannel streamChannel = config.getStreamChannel();
            
            // 如果流式载体和默认载体不同，确保发送最终消息
            // 如果相同，流式输出已经在默认载体中显示了，无需重复发送
            if (defaultChannel != streamChannel) {
                // 添加 AI 前缀（与 pipeline.send() 保持一致）
                String formattedMessage = com.zm.kilacraftAI.util.MessageUtil.getAIPrefix() + finalMessage;
                
                // 确保在主线程上发送最终消息
                if (plugin.getServer().isPrimaryThread()) {
                    dispatchToChannel(player, formattedMessage, defaultChannel);
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        dispatchToChannel(player, formattedMessage, defaultChannel);
                    });
                }
            }
        }

        // 延迟清理状态（避免立即清理导致闪烁）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playerStates.remove(playerId);
        }, 20L); // 1秒后清理
    }

    /**
     * 取消流式生成（异常场景）
     *
     * @param player 目标玩家
     */
    public void cancelGeneration(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        playerStates.remove(playerId);
    }

    /**
     * 根据载体类型分发显示
     */
    private void dispatchToChannel(Player player, String message, OutputChannel channel) {
        dispatcher.dispatch(player, message, channel);
    }

    /**
     * 清理所有流式状态（插件卸载时调用）
     *
     * <p>释放资源：</p>
     * <ul>
     *   <li>清理所有玩家的流式状态映射</li>
     *   <li>取消所有延迟清理任务（由 Bukkit 自动处理）</li>
     * </ul>
     */
    public void cleanup() {
        playerStates.clear();
    }
}
