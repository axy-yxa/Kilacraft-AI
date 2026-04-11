package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.afktask.AFKTask;
import com.zm.kilacraftAI.skills.afktask.AFKTaskCallback;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.task.LLMAnalysisService;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLevelChangeEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家等级变化监视任务
 *
 * <p>监听指定玩家等级变化事件，当目标玩家升级或降级时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>与 PlayerOnlineWatchTask 完全对称</li>
 *   <li>可获取变化前后的等级</li>
 *   <li>本任务只负责监视等级变化事件，回调逻辑与上线监视一致</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着 Steve，他升级了告诉我"</li>
 *   <li>"监视玩家A，他升级后查询他的新等级"</li>
 * </ul>
 *
 * <h3>必需参数：</h3>
 * <ul>
 *   <li>target_player: 目标玩家名称（被监视的玩家）</li>
 *   <li>callback: 回调配置（AFKTaskCallback JSON 格式，可选）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerLevelChangeWatchTask extends AFKTask implements Listener {

    private static final Gson GSON = new Gson();

    /**
     * 目标玩家名称（被监视的玩家）
     */
    private final String targetPlayerName;

    /**
     * 回调配置（多步骤任务定义）
     */
    private final AFKTaskCallback callback;

    /**
     * 是否已注册事件监听器
     */
    private boolean listenerRegistered = false;

    /**
     * 构造玩家等级变化监视任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：target_player
     *                    可选：callback（JSON格式）
     */
    public PlayerLevelChangeWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_LEVEL_CHANGE_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
        this.callback = parseCallback(getParam("callback", ""));
    }

    /**
     * 解析回调配置 JSON
     */
    private AFKTaskCallback parseCallback(String json) {
        if (json == null || json.isEmpty()) {
            return new AFKTaskCallback();
        }
        try {
            return GSON.fromJson(json, AFKTaskCallback.class);
        } catch (Exception e) {
            plugin.getLogger().warning("[挂机任务] 解析回调配置失败: " + e.getMessage());
            return new AFKTaskCallback();
        }
    }

    @Override
    public void start() {
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            notifyPlayer("§c任务创建失败：缺少目标玩家名称参数。");
            complete("任务参数不完整，已自动取消。");
            return;
        }

        // 回调配置可选：如果为空或无步骤，则为纯通知模式
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        // 注册事件监听器
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
            markRunning();

            // 启动通知由上游 AIRequestHandler 通过 LLM 二次分析发送，此处不再重复通知玩家

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] [挂机任务] 已启动: " + getTaskId() + ", 目标: " + targetPlayerName + ", 模式: " + (hasCallback ? "回调(" + callback.getCallbackTask().getSteps().size() + "步)" : "纯通知"));
            }
        } catch (Exception e) {
            notifyPlayer("§c任务启动失败：" + e.getMessage());
            complete("任务启动异常，已自动取消。");
        }
    }

    @Override
    protected void onStop() {
        // 注销事件监听器
        if (listenerRegistered) {
            try {
                HandlerList.unregisterAll(this);
                listenerRegistered = false;

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] [挂机任务] 已停止: " + getTaskId());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[挂机任务] 注销事件监听器失败: " + e.getMessage());
            }
        }
    }

    /**
     * 监听玩家等级变化事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 检查是否是目标玩家
        String changedPlayerName = event.getPlayer().getName();
        if (!changedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 目标玩家等级变化
        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        if (hasCallback) {
            // 有回调步骤：执行多步骤回调任务
            executeCallback(changedPlayerName, oldLevel, newLevel);
        } else {
            // 纯通知模式：直接通知等级变化
            String direction = newLevel > oldLevel ? "升级" : "降级";
            String message = String.format(
                "§a§l🔔 监视任务完成\n\n" +
                "§f• 目标玩家：§e%s\n" +
                "§f• 状态：§e%s\n" +
                "§f• 旧等级：§f%d\n" +
                "§f• 新等级：§f%d\n\n" +
                "§f%s %s 了！",
                changedPlayerName,
                direction,
                oldLevel,
                newLevel,
                changedPlayerName,
                direction
            );
            notifyPlayer(message);
            complete("目标玩家 " + changedPlayerName + " 等级变化（" + oldLevel + " → " + newLevel + "），监视任务完成。");
        }
    }

    /**
     * 执行回调任务
     *
     * @param triggeredPlayerName 触发事件的玩家名称
     * @param oldLevel 旧等级
     * @param newLevel 新等级
     */
    private void executeCallback(String triggeredPlayerName, int oldLevel, int newLevel) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            replacePlaceholdersInTaskPlan(plan, triggeredPlayerName, oldLevel, newLevel);

            // 2. 获取任务创建者玩家对象
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                plugin.getLogger().warning("[挂机任务] 任务创建者不在线，无法执行回调: " + getTaskId());
                complete("任务创建者不在线，回调任务已取消。");
                return;
            }

            // 3. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 4. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 5. 执行多步骤任务
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager(), new LLMAnalysisService());

            CompletableFuture<SkillResult> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 6. 处理执行结果
            future.thenAccept(result -> {
                // 7. 通知玩家
                notifyCallbackResult(triggeredPlayerName, result);

                // 8. 完成任务
                complete("目标玩家 " + triggeredPlayerName + " 等级变化，回调任务已执行。");
            }).exceptionally(ex -> {
                plugin.getLogger().severe("[挂机任务] 回调任务执行异常: " + ex.getMessage());
                ex.printStackTrace();
                notifyPlayer("§c回调任务执行失败：" + ex.getMessage());
                complete("回调任务执行异常。");
                return null;
            });

        } catch (Exception e) {
            notifyPlayer("§c回调任务启动失败：" + e.getMessage());
            plugin.getLogger().severe("[挂机任务] 回调任务启动异常: " + e.getMessage());
            e.printStackTrace();
            complete("回调任务启动异常。");
        }
    }

    /**
     * 替换 TaskPlan 中的占位符
     */
    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, int oldLevel, int newLevel) {
        String direction = newLevel > oldLevel ? "升级" : "降级";
        
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value
                    .replace("{triggered_player}", triggeredPlayerName)
                    .replace("{creator}", getPlayerName())
                    .replace("{old_level}", String.valueOf(oldLevel))
                    .replace("{new_level}", String.valueOf(newLevel))
                    .replace("{direction}", direction);
            });
        });
    }

    /**
     * 通知回调任务执行结果
     *
     * @param triggeredPlayerName 触发事件的玩家名称
     * @param result              LLM 二次分析后的结果
     */
    private void notifyCallbackResult(String triggeredPlayerName, SkillResult result) {
        String notifyTarget = callback.getNotifyTarget();

        if (notifyTarget == null || notifyTarget.isEmpty()) {
            notifyTarget = "{creator}";  // 默认通知任务创建者
        }

        // LLM 二次分析的完整结果
        String analysisResult = result.getMessage() != null ? result.getMessage() : "无结果";

        // 构建完整通知
        String header = "§f§l🔔 挂机任务提醒\n\n";
        String body = MessageUtil.convertMarkdownToMinecraft(analysisResult);
        String fullMessage = header + body;

        // 发送到目标玩家
        if (notifyTarget.equals("{creator}")) {
            notifyPlayer(fullMessage);
        } else {
            Player targetPlayer = Bukkit.getPlayerExact(notifyTarget);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(MessageUtil.getAIPrefix() + fullMessage);
            }
        }
    }

    @Override
    public String getTaskDescription() {
        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? "，目标：" + goal : "";
            return "监视玩家 " + targetPlayerName + " 等级变化，触发回调任务（" + callback.getCallbackTask().getSteps().size() + "步）" + goalDesc;
        }
        return "监视玩家 " + targetPlayerName + " 等级变化，变化后通知创建者（纯通知）";
    }
}
