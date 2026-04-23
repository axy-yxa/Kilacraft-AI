package com.zm.kilacraftAI.skills.afktask;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;

/**
 * 挂机任务抽象基类
 *
 * <p>所有挂机任务都必须继承此类，实现具体的条件检查和资源清理逻辑。</p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>构造 → PENDING</li>
 *   <li>start() → 注册事件监听/启动轮询 → RUNNING</li>
 *   <li>条件满足 → 通知玩家 → COMPLETED</li>
 *   <li>stop() → 清理资源 → CANCELLED</li>
 * </ol>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
@Getter
public abstract class AFKTask {

    protected final KilacraftAI plugin = KilacraftAI.getInstance();

    private final String taskId;
    private final UUID playerUUID;
    private final String playerName;
    private final AFKTaskType taskType;
    private final String description;
    private final Map<String, String> params;
    private volatile AFKTaskStatus status;
    private final long createdAt;

    /**
     * 启动阶段的错误消息（由子类在 start() 中通过 failStart 设置）
     */
    private String startError;

    /**
     * 构造挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID
     * @param playerName  玩家名称
     * @param taskType    任务类型
     * @param description 任务描述
     * @param params      任务参数
     */
    protected AFKTask(String taskId, UUID playerUUID, String playerName, AFKTaskType taskType, String description, Map<String, String> params) {
        this.taskId = taskId;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.taskType = taskType;
        this.description = description;
        this.params = params;
        this.status = AFKTaskStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 启动任务
     *
     * <p>子类实现此方法来注册事件监听器或启动定时轮询。</p>
     * <p>实现中必须调用 {@link #markRunning()} 来更新状态。</p>
     */
    public abstract void start();

    /**
     * 停止任务（清理资源）
     *
     * <p>子类实现此方法来注销事件监听器或取消定时任务。</p>
     * <p>框架会自动更新状态为 CANCELLED，子类不需要手动更新状态。</p>
     */
    protected abstract void onStop();

    /**
     * 获取任务的可读描述（用于状态查询和通知）
     *
     * @return 任务描述
     */
    public abstract String getTaskDescription();

    /**
     * 标记任务为运行中
     */
    protected void markRunning() {
        this.status = AFKTaskStatus.RUNNING;
    }

    /**
     * 在 start() 阶段因前置条件不满足而终止任务
     *
     * <p>子类在 start() 中检测到参数缺失、注册监听器失败等错误时调用此方法，
     * 会记录错误消息并完成任务。</p>
     *
     * @param errorMessage 具体的错误描述（会传递给 AFKTaskManager 作为 SkillResult.failure 的消息）
     */
    protected void failStart(String errorMessage) {
        this.startError = I18nService.tr(errorMessage);
        complete(I18nService.tr("任务启动失败: {}", this.startError));
    }

    /**
     * 终止任务并释放资源
     *
     * <p>将任务状态标记为 COMPLETED，调用子类资源清理并从管理器中移除引用。此方法适用场景包括：
     * <ul>
     *   <li>正常完成：监视条件满足（如目标玩家上线）</li>
     *   <li>异常终止：启动阶段前置条件不满足（通过 {@link #failStart} 间接调用）</li>
     *   <li>回调执行完毕</li>
     * </ul>
     * 如果任务已经处于终态（COMPLETED/CANCELLED），则忽略调用。</p>
     *
     * <p>注意：此方法会调用 {@link #onStop()} 确保子类资源（事件监听器、定时任务等）被正确释放，
     * 防止内存泄漏。所有子类的 onStop() 实现必须是幂等的。</p>
     *
     * @param message 终止原因描述（用于日志记录）
     */
    protected void complete(String message) {
        if (this.status == AFKTaskStatus.COMPLETED || this.status == AFKTaskStatus.CANCELLED) {
            return;
        }
        this.status = AFKTaskStatus.COMPLETED;
        onStop();    // 释放子类资源（事件监听器、定时任务等）
        cleanup();
        PluginLogger.debug("挂机任务", "任务终止: {} - {}", taskId, message);
    }

    /**
     * 停止任务（外部调用入口）
     */
    public final void stop() {
        if (this.status == AFKTaskStatus.COMPLETED || this.status == AFKTaskStatus.CANCELLED) {
            return;
        }
        this.status = AFKTaskStatus.CANCELLED;
        onStop();
        PluginLogger.debug("挂机任务", "任务已取消: {}", taskId);
    }

    /**
     * 从管理器中移除任务引用
     */
    private void cleanup() {
        AFKTaskManager manager = plugin.getAfkTaskManager();
        if (manager != null) {
            manager.removeTask(this.playerUUID, this.taskId);
        }
    }

    /**
     * 通知玩家（通过游戏内消息）
     *
     * @param message 消息内容
     */
    protected void notifyPlayer(String message) {
        if (playerUUID == null) return;
        var player = plugin.getServer().getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            // 使用统一响应管线（挂机任务回调场景）
            plugin.getResponsePipeline().send(player, I18nService.tr(message), OutputScenario.AFK_CALLBACK);
        }
    }

    /**
     * 通过 LLM 二次分析通知玩家（用于纯通知模式）
     *
     * <p>将事件信息构建为 AnalysisSummary，通过中间层进行 LLM 二次分析后输出</p>
     *
     * @param eventDescription 事件描述（面向 LLM，不是面向玩家）
     */
    protected void notifyWithLLMAnalysis(String eventDescription) {
        if (playerUUID == null) return;
        Player player = plugin.getServer().getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }

        // 构建结构化摘要（面向 LLM）
        AnalysisSummary summary = new AnalysisSummary().userMessage(description).addResult("SUCCESS", eventDescription).statistics(1, 0, 0);

        // 构建上下文
        SkillContext context = new SkillContext(player, description, params);

        // 通过中间层输出（不显示占位符）
        LLMOutputCoordinator coordinator = plugin.getLlmOutputCoordinator();
        coordinator.outputAnalysisResult(player, summary, context, new ArrayDeque<>(), OutputScenario.AFK_CALLBACK, false  // 挂机任务回调不显示占位符
        );
    }

    /**
     * 获取任务参数值
     *
     * @param key 参数键
     * @return 参数值，不存在则返回 null
     */
    public String getParam(String key) {
        return params != null ? params.get(key) : null;
    }

    /**
     * 获取任务参数值（带默认值）
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getParam(String key, String defaultValue) {
        String value = getParam(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 是否仍在活跃状态
     *
     * @return true 如果任务还在运行或等待中
     */
    public boolean isActive() {
        return status == AFKTaskStatus.PENDING || status == AFKTaskStatus.RUNNING;
    }

    /**
     * 获取任务状态的可读文本
     *
     * @return 状态中文描述
     */
    public String getStatusText() {
        return switch (status) {
            case PENDING -> I18nService.tr("等待启动");
            case RUNNING -> I18nService.tr("运行中");
            case COMPLETED -> I18nService.tr("已完成");
            case CANCELLED -> I18nService.tr("已取消");
        };
    }
}
