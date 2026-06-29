package com.zm.kilacraftAI.service.afktask;

import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.afktask.AFKTask;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂机任务管理器
 *
 * <p>管理所有挂机任务的创建、查询、取消和生命周期。</p>
 *
 * <h3>核心约束：</h3>
 * <ul>
 *   <li>一个玩家只能同时拥有一个挂机任务</li>
 *   <li>全局最大并发任务数受配置限制</li>
 *   <li>玩家下线自动取消任务</li>
 *   <li>任务完成后自动释放资源</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class AFKTaskManager {

    private final int maxTasks;

    /**
     * 玩家UUID → 活跃任务（一人一任务）
     */
    private final Map<UUID, AFKTask> taskMap = new ConcurrentHashMap<>();

    public AFKTaskManager(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    /**
     * 创建挂机任务
     *
     * @param player      玩家
     * @param taskType    任务类型
     * @param description 任务描述
     * @param params      任务参数
     * @param taskFactory 任务工厂（用于创建具体的 AFKTask 子类实例）
     * @return 创建结果
     */
    public synchronized SkillResult createTask(Player player, AFKTaskTypeEnum taskType, String description, Map<String, String> params, AFKTaskFactory taskFactory) {
        UUID playerUUID = player.getUniqueId();

        // 检查是否已有任务
        if (hasTask(playerUUID)) {
            AFKTask existing = taskMap.get(playerUUID);
            return SkillResult.failure(I18nService.tr("玩家已有一个正在运行的挂机任务：{}。请用自然语言告知玩家当前有任务在运行，并建议：可以使用 /kila afk cancel 命令取消旧任务后再创建新的，或使用 /kila afk query 查询详情。", existing.getTaskDescription()));
        }

        // 检查队列是否已满
        if (isFull()) {
            return SkillResult.failure(I18nService.tr("挂机任务队列已满（当前 {}/{}），暂时无法创建新任务。请用自然语言告知玩家稍后再试。", getActiveTaskCount(), maxTasks));
        }

        // 生成任务ID
        String taskId = generateTaskId(playerUUID);

        // 通过工厂创建具体任务
        AFKTask task = taskFactory.create(taskId, playerUUID, player.getName(), taskType, description, params);

        if (task == null) {
            return SkillResult.failure("无法创建该类型的挂机任务，请检查任务参数是否正确。");
        }

        // 注册任务（putIfAbsent 原子操作，防止并发重复注册）
        AFKTask previous = taskMap.putIfAbsent(playerUUID, task);
        if (previous != null) {
            // 极端并发：在 hasTask 检查和注册之间被其他线程抢先注册
            return SkillResult.failure(I18nService.tr("玩家已有一个正在运行的挂机任务：{}。请用自然语言告知玩家当前有任务在运行，并建议：可以使用 /kila afk cancel 命令取消旧任务后再创建新的，或使用 /kila afk query 查询详情。", previous.getTaskDescription()));
        }

        // 原子检查总数量（防止并发创建超过 maxTasks）
        if (taskMap.size() > maxTasks) {
            taskMap.remove(playerUUID);
            return SkillResult.failure(I18nService.tr("挂机任务队列已满（当前 {}/{}），暂时无法创建新任务。请用自然语言告知玩家稍后再试。", taskMap.size(), maxTasks));
        }

        // 启动任务
        task.start();

        // 检查任务是否在start()内部立即完成了（如参数不完整自动取消）
        if (!task.isActive()) {
            // 清理注册（参数校验失败时）
            taskMap.remove(playerUUID);
            String errorMsg = task.getStartError() != null ? task.getStartError() : "任务启动失败，未知原因";
            return SkillResult.failure(I18nService.tr("挂机任务创建失败：{}", errorMsg));
        }

        PluginLoggerUtil.debug("挂机任务", "已创建: {}, {}, {}, 总数: {}", taskId, taskType, player.getName(), getActiveTaskCount());

        return SkillResult.success(I18nService.tr("挂机任务已创建并启动：{}", task.getTaskDescription()));
    }

    /**
     * 取消玩家的挂机任务
     *
     * @param playerUUID 玩家UUID
     * @return 取消结果
     */
    public SkillResult cancelTask(UUID playerUUID) {
        AFKTask task = taskMap.remove(playerUUID);
        if (task == null) {
            return SkillResult.failure("你当前没有正在运行的挂机任务。");
        }
        task.stop();
        return SkillResult.success(I18nService.tr("挂机任务已取消：{}", task.getTaskDescription()));
    }

    /**
     * 查询玩家的当前挂机任务
     *
     * @param playerUUID 玩家UUID
     * @return 任务信息，null 表示没有任务
     */
    public AFKTask getTask(UUID playerUUID) {
        return taskMap.get(playerUUID);
    }

    /**
     * 玩家是否已有挂机任务
     */
    public boolean hasTask(UUID playerUUID) {
        return taskMap.containsKey(playerUUID);
    }

    /**
     * 当前活跃任务数
     */
    public int getActiveTaskCount() {
        return taskMap.size();
    }

    /**
     * 队列是否已满
     */
    public boolean isFull() {
        return taskMap.size() >= maxTasks;
    }

    /**
     * 玩家下线时自动清理
     *
     * @param playerUUID 玩家UUID
     */
    public void onPlayerQuit(UUID playerUUID) {
        AFKTask task = taskMap.remove(playerUUID);
        if (task != null) {
            task.stop();
            PluginLoggerUtil.debug("挂机任务", "玩家下线，自动取消任务: {}, 玩家: {}", task.getTaskId(), task.getPlayerName());
        }
    }

    /**
     * 移除任务引用（由 AFKTask.complete() 内部调用）
     *
     * @param playerUUID 玩家UUID
     * @param taskId     任务ID
     */
    public void removeTask(UUID playerUUID, String taskId) {
        taskMap.remove(playerUUID);
    }

    /**
     * 关闭所有任务（服务器关闭时调用）
     */
    public void shutdown() {
        if (!taskMap.isEmpty()) {
            PluginLoggerUtil.info("挂机任务", "正在关闭 {} 个活跃任务...", taskMap.size());
            for (AFKTask task : taskMap.values()) {
                task.stop();
            }
            taskMap.clear();
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(UUID playerUUID) {
        return "afk_" + playerUUID.toString().substring(0, 8) + "_" + System.currentTimeMillis() % 10000;
    }

    /**
     * 挂机任务工厂接口
     *
     * <p>用于解耦 AFKTaskManager 和具体的 AFKTask 子类。</p>
     * <p>由 AFKTaskSkill 根据任务类型提供不同的工厂实现。</p>
     */
    @FunctionalInterface
    public interface AFKTaskFactory {
        AFKTask create(String taskId, UUID playerUUID, String playerName, AFKTaskTypeEnum taskType, String description, Map<String, String> params);
    }
}
