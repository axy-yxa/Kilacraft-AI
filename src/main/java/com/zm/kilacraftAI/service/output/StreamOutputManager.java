package com.zm.kilacraftAI.service.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.common.util.MessageUtil;
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

    /**
     * 获取 MessageDispatcher（延迟获取 Pipeline 的 dispatcher，确保 BOSS_BAR 等资源由同一管理器管理）
     */
    private MessageDispatcher getDispatcher() {
        return plugin.getResponsePipeline().getDispatcher();
    }

    /**
     * 流式生成状态
     */
    public enum GenerationState {
        /**
         * 空闲（无流式生成）
         */
        IDLE,
        /**
         * 生成中（窗口期 + 流式片段接收中）
         */
        GENERATING,
        /**
         * 已完成（流式生成结束，等待清理）
         */
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
     * <p>在 AI 请求发起时调用，立即显示"生成中..."占位符</p>
     * <p>线程安全：如果从异步线程调用，会自动切换到同步线程</p>
     *
     * @param player  目标玩家
     * @param channel 输出载体（由调用方传入场景配置）
     */
    public void startGeneration(Player player, OutputChannelEnum channel) {
        startGeneration(player, channel, false);
    }

    /**
     * 开始流式生成
     *
     * @param player  目标玩家
     * @param channel 输出载体（由调用方传入场景配置）
     * @param silent  是否静默启动（不显示占位符，仅初始化状态机）
     */
    public void startGeneration(Player player, OutputChannelEnum channel, boolean silent) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        // 线程安全：切换到同步线程
        if (FoliaCompat.isPrimaryThread()) {
            startGenerationSync(player, channel, silent);
        } else {
            FoliaCompat.runTask(plugin, () -> startGenerationSync(player, channel, silent));
        }
    }

    /**
     * 同步开始流式生成（必须在主线程/区域线程调用）
     */
    private void startGenerationSync(Player player, OutputChannelEnum channel, boolean silent) {
        UUID playerId = player.getUniqueId();

        // 设置状态为 GENERATING
        playerStates.put(playerId, GenerationState.GENERATING);

        // 如果不是静默模式，立即显示占位符
        if (!silent) {
            String placeholder = getPlaceholderMessage();
            dispatchToChannel(player, placeholder, channel);
        }
    }

    /**
     * 更新流式内容（接收片段）
     *
     * <p>每收到一个 LLM 片段时调用，更新显示内容</p>
     * <p>状态检查：只在 GENERATING 状态下接受更新，防止竞态条件</p>
     * <p>线程安全：LLM 回调在异步线程，必须切换到同步线程调用 Bukkit API</p>
     *
     * @param player      目标玩家
     * @param chunk       新片段（当前未使用，保留用于未来日志/统计扩展）
     * @param fullMessage 当前累积的完整消息（用于显示）
     * @param channel     输出载体（由调用方传入场景配置）
     */
    public void updateStreamChunk(Player player, String chunk, String fullMessage, OutputChannelEnum channel) {
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

        // 线程安全：LLM 回调在异步线程，必须切换到同步线程
        if (FoliaCompat.isPrimaryThread()) {
            updateStreamChunkSync(player, chunk, fullMessage, channel);
        } else {
            // Folia: 使用 GlobalRegionScheduler
            // Spigot: 使用 Bukkit.getScheduler().runTask()
            FoliaCompat.runTask(plugin, () -> updateStreamChunkSync(player, chunk, fullMessage, channel));
        }
    }

    /**
     * 同步更新流式内容（必须在主线程/区域线程调用）
     */
    private void updateStreamChunkSync(Player player, String chunk, String fullMessage, OutputChannelEnum channel) {
        // 双重检查：防止线程切换期间状态变化
        UUID playerId = player.getUniqueId();
        GenerationState state = playerStates.get(playerId);
        if (state != GenerationState.GENERATING) {
            return;
        }

        // 将 Markdown 转换为 Minecraft 格式，再添加 ai_prefix（与最终消息保持一致）
        // SIDEBAR 载体：ScoreboardManager.sendSidebar 内部会自动 removePrefix 再用 getAIPrefix() 做 title
        String converted = MessageUtil.convertMarkdownToMinecraft(fullMessage);
        String prefixedMessage = MessageUtil.getAIPrefix() + converted;
        dispatchToChannel(player, prefixedMessage, channel);
    }

    /**
     * 完成流式生成
     *
     * <p>LLM 响应完成后调用，设置 COMPLETED 状态并更新载体为带前缀的最终消息</p>
     * <p>线程安全：LLM 回调在异步线程，必须切换到同步线程</p>
     *
     * @param player           目标玩家
     * @param formattedMessage 已格式化的最终消息（带 ai_prefix）
     * @param channel          输出载体
     */
    public void completeGeneration(Player player, String formattedMessage, OutputChannelEnum channel) {
        if (player == null || !config.isStreamEnabled()) {
            return;
        }

        // 线程安全：切换到同步线程
        if (FoliaCompat.isPrimaryThread()) {
            completeGenerationSync(player, formattedMessage, channel);
        } else {
            FoliaCompat.runTask(plugin, () -> completeGenerationSync(player, formattedMessage, channel));
        }
    }

    /**
     * 同步完成流式生成（必须在主线程/区域线程调用）
     *
     * <p>在主线程中原子执行：先设置 COMPLETED 状态拒绝后续 chunk，再更新载体为最终内容</p>
     * <p>这保证了最终带前缀的内容不会被后续残留 chunk 覆盖</p>
     */
    private void completeGenerationSync(Player player, String formattedMessage, OutputChannelEnum channel) {
        UUID playerId = player.getUniqueId();

        // 先设置 COMPLETED，拒绝后续的 updateStreamChunk
        playerStates.put(playerId, GenerationState.COMPLETED);

        // 更新载体为带前缀的最终消息（在 COMPLETED 之后，不会被后续 chunk 覆盖）
        dispatchToChannel(player, formattedMessage, channel);

        // 延迟清理状态（避免立即清理导致闪烁）
        FoliaCompat.runTaskLater(plugin, () -> playerStates.remove(playerId), 20L); // 1秒后清理
    }

    /**
     * 取消流式生成（异常场景）
     *
     * <p>线程安全：如果从异步线程调用，会自动切换到同步线程</p>
     *
     * @param player 目标玩家
     */
    public void cancelGeneration(Player player) {
        if (player == null) {
            return;
        }

        // 线程安全：切换到同步线程
        if (FoliaCompat.isPrimaryThread()) {
            cancelGenerationSync(player);
        } else {
            FoliaCompat.runTask(plugin, () -> cancelGenerationSync(player));
        }
    }

    /**
     * 同步取消流式生成（必须在主线程/区域线程调用）
     */
    private void cancelGenerationSync(Player player) {
        UUID playerId = player.getUniqueId();
        playerStates.remove(playerId);
    }

    /**
     * 根据载体类型分发显示
     */
    private void dispatchToChannel(Player player, String message, OutputChannelEnum channel) {
        // SIDEBAR 载体需要特殊处理：title 使用 ai_prefix，content 不带 prefix
        if (channel == OutputChannelEnum.SIDEBAR) {
            // 直接调用 ScoreboardManager，让它自动处理 prefix
            getDispatcher().getScoreboardManager().sendSidebar(player, message);
            return;
        }

        getDispatcher().dispatch(player, message, channel);
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
