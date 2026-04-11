package com.zm.kilacraftAI.skills.afktask;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.MessageUtil;
import lombok.Getter;

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
    private AFKTaskStatus status;
    private final long createdAt;

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
     * 条件满足时完成任务
     *
     * <p>子类在检测到条件满足时调用此方法，会自动通知玩家并释放资源。</p>
     *
     * @param completionMessage 完成通知消息
     */
    protected void complete(String completionMessage) {
        if (this.status == AFKTaskStatus.COMPLETED || this.status == AFKTaskStatus.CANCELLED) {
            return;
        }
        this.status = AFKTaskStatus.COMPLETED;
        cleanup();
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] [挂机任务] 任务完成: " + taskId + " - " + completionMessage);
        }
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
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] [挂机任务] 任务已取消: " + taskId);
        }
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
            player.sendMessage(MessageUtil.getAIPrefix() + MessageUtil.convertMarkdownToMinecraft(message));
        }
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
            case PENDING -> "等待启动";
            case RUNNING -> "运行中";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }
}
