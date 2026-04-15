package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.zm.kilacraftAI.enums.OutputScenario;
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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 天气变化挂机任务
 *
 * <p>监听世界天气变化事件，当指定世界的天气发生变化时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>与 PlayerOnlineWatchTask 对称，但监听的是世界事件而非玩家事件</li>
 *   <li>可获取天气变化前后的状态</li>
 *   <li>本任务只负责监视天气变化事件，回调逻辑与上线监视一致</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着主世界，下雨了告诉我"</li>
 *   <li>"监视世界天气，天气变化后查询当前天气状态"</li>
 * </ul>
 *
 * <h3>必需参数：</h3>
 * <ul>
 *   <li>target_world: 目标世界名称（被监视的世界，可选，默认为玩家当前世界）</li>
 *   <li>callback: 回调配置（AFKTaskCallback JSON 格式，可选）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class WeatherChangeWatchTask extends AFKTask implements Listener {

    private static final Gson GSON = new Gson();

    /**
     * 目标世界名称（被监视的世界，为空则监视玩家当前世界）
     */
    private final String targetWorldName;

    /**
     * 回调配置（多步骤任务定义）
     */
    private final AFKTaskCallback callback;

    /**
     * 是否已注册事件监听器
     */
    private boolean listenerRegistered = false;

    /**
     * 构造天气变化挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    可选：target_world（世界名称，为空则监视玩家当前世界）
     *                    可选：callback（JSON格式）
     */
    public WeatherChangeWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.WEATHER_CHANGE_WATCH, description, params);
        this.targetWorldName = getParam("target_world", "");
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
        // target_world 可选，如果为空则监视玩家当前世界
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        // 注册事件监听器
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
            markRunning();

            // 启动通知由上游 AIRequestHandler 通过 LLM 二次分析发送，此处不再重复通知玩家

            if (plugin.getConfigManager().isDebugMode()) {
                String worldDesc = (targetWorldName != null && !targetWorldName.isEmpty()) ? targetWorldName : "玩家当前世界";
                plugin.getLogger().info("[DEBUG] [挂机任务] 已启动: " + getTaskId() + ", 目标世界: " + worldDesc + ", 模式: " + (hasCallback ? "回调(" + callback.getCallbackTask().getSteps().size() + "步)" : "纯通知"));
            }
        } catch (Exception e) {
            failStart("监听器注册失败: " + e.getMessage());
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
     * 监听天气变化事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        World eventWorld = event.getWorld();
        
        // 检查是否是目标世界
        if (targetWorldName != null && !targetWorldName.isEmpty()) {
            // 指定了目标世界，检查是否匹配
            if (!eventWorld.getName().equalsIgnoreCase(targetWorldName)) {
                return;
            }
        } else {
            // 未指定目标世界，检查是否是玩家当前世界
            Player player = Bukkit.getPlayer(getPlayerUUID());
            if (player == null || !player.isOnline()) {
                return;
            }
            if (!player.getWorld().getName().equalsIgnoreCase(eventWorld.getName())) {
                return;
            }
        }

        // 天气变化
        boolean toWeatherState = event.toWeatherState();  // true = 雨天/雷暴, false = 晴天
        String weatherDesc = toWeatherState ? "开始下雨/雷暴" : "天气转晴";
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        if (hasCallback) {
            // 先完成任务：立即注销事件监听器，防止异步回调期间新事件触发重复回调
            complete("世界 " + eventWorld.getName() + " 天气变化（" + weatherDesc + "），开始执行回调。");
            executeCallback(eventWorld.getName(), toWeatherState, weatherDesc);
        } else {
            // 纯通知模式：直接通知天气变化
            String message = String.format(
                "§a§l🔔 挂机任务完成\n\n" +
                "§f• 世界：§e%s\n" +
                "§f• 状态：§e%s\n" +
                "§f• 新天气：§f%s\n\n" +
                "§f%s 的天气变化了！",
                eventWorld.getName(),
                weatherDesc,
                toWeatherState ? "雨天/雷暴" : "晴天",
                eventWorld.getName()
            );
            notifyPlayer(message);
            complete("世界 " + eventWorld.getName() + " 天气变化（" + weatherDesc + "），挂机任务完成。");
        }
    }

    /**
     * 执行回调任务
     *
     * @param worldName 世界名称
     * @param toWeatherState 新的天气状态（true=雨天，false=晴天）
     * @param weatherDesc 天气变化描述
     */
    private void executeCallback(String worldName, boolean toWeatherState, String weatherDesc) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            replacePlaceholdersInTaskPlan(plan, worldName, toWeatherState, weatherDesc);

            // 2. 获取任务创建者玩家对象
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                plugin.getLogger().warning("[挂机任务] 任务创建者不在线，无法执行回调: " + getTaskId());
                notifyPlayer("§c任务创建者不在线，回调任务已取消。");
                return;
            }

            // 3. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 4. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 5. 执行多步骤任务
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager(), new LLMAnalysisService());

            CompletableFuture<SkillResult> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 6. 处理执行结果（注意：任务已在调用方通过 complete() 完成，此处仅做通知）
            future.thenAccept(result -> {
                notifyCallbackResult(result);
            }).exceptionally(ex -> {
                plugin.getLogger().severe("[挂机任务] 回调任务执行异常: " + ex.getMessage());
                ex.printStackTrace();
                notifyPlayer("§c回调任务执行失败：" + ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            notifyPlayer("§c回调任务启动失败：" + e.getMessage());
            plugin.getLogger().severe("[挂机任务] 回调任务启动异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 替换 TaskPlan 中的占位符
     */
    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String worldName, boolean toWeatherState, String weatherDesc) {
        String weatherType = toWeatherState ? "雨天/雷暴" : "晴天";
        
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value
                    .replace("{world_name}", worldName)
                    .replace("{creator}", getPlayerName())
                    .replace("{weather_state}", weatherDesc)
                    .replace("{weather_type}", weatherType);
            });
        });
    }

    /**
     * 通知回调任务执行结果
     *
     * @param result              LLM 二次分析后的结果
     */
    private void notifyCallbackResult(SkillResult result) {
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
                // 使用统一响应管线（挂机任务回调场景）
                plugin.getResponsePipeline().send(targetPlayer, fullMessage, OutputScenario.AFK_CALLBACK);
            }
        }
    }

    @Override
    public String getTaskDescription() {
        String worldDesc = (targetWorldName != null && !targetWorldName.isEmpty()) ? targetWorldName : "当前世界";
        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? "，目标：" + goal : "";
            return "监视世界 " + worldDesc + " 天气变化，触发回调任务（" + callback.getCallbackTask().getSteps().size() + "步）" + goalDesc;
        }
        return "监视世界 " + worldDesc + " 天气变化，变化后通知创建者（纯通知）";
    }
}
