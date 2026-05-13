package com.zm.kilacraftAI.skills.afktask;

import com.google.gson.Gson;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 事件型挂机任务的抽象基类
 *
 * <p>提取了所有事件型 WatchTask 的公共逻辑，包括：</p>
 * <ul>
 *   <li>回调配置解析（JSON → AFKTaskCallback）</li>
 *   <li>回调执行模板（构建 TaskPlan → 占位符替换 → TaskExecutor → LLM 二次分析）</li>
 *   <li>事件监听器生命周期管理（注册/注销）</li>
 *   <li>并发防护（AtomicBoolean）</li>
 *   <li>通用参数解析工具方法</li>
 * </ul>
 *
 * <h3>模板方法模式：</h3>
 * <p>子类通过 {@link #executeCallback(Consumer)} 传入各自的占位符替换逻辑，
 * 核心执行流程（获取创建者 → 构建上下文 → 执行任务 → 处理结果 → 错误处理）统一在此类中实现。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-27
 */
public abstract class AbstractEventWatchTask extends AFKTask implements Listener {

    private static final Gson GSON = new Gson();

    /**
     * 回调配置（多步骤任务定义）
     */
    private final AFKTaskCallback callback;

    /**
     * 是否已注册事件监听器（volatile：可能从异步线程读取，如 CustomWatchTask 的 stopPolling）
     */
    private volatile boolean listenerRegistered = false;

    /**
     * 回调执行标志（防止并发重复执行）
     */
    private final AtomicBoolean callbackExecuted = new AtomicBoolean(false);

    /**
     * 构造事件型挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID
     * @param playerName  玩家名称
     * @param taskType    任务类型
     * @param description 任务描述
     * @param params      任务参数（自动解析 callback 参数）
     */
    protected AbstractEventWatchTask(String taskId, UUID playerUUID, String playerName, AFKTaskType taskType, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, taskType, description, params);
        this.callback = parseCallback(getParam("callback", ""));
    }

    // ==================== 回调配置解析 ====================

    /**
     * 解析回调配置 JSON
     *
     * @param json 回调配置 JSON 字符串
     * @return 解析后的 AFKTaskCallback，解析失败返回空对象
     */
    private AFKTaskCallback parseCallback(String json) {
        if (json == null || json.isEmpty()) {
            return new AFKTaskCallback();
        }
        try {
            return GSON.fromJson(json, AFKTaskCallback.class);
        } catch (Exception firstError) {
            // LLM 经常生成结构不完整的嵌套 JSON（缺少闭合花括号）
            // 尝试自动修复：补全缺失的 }
            String repaired = repairJsonBraces(json);
            if (!repaired.equals(json)) {
                try {
                    AFKTaskCallback result = GSON.fromJson(repaired, AFKTaskCallback.class);
                    PluginLogger.debug("挂机任务", I18nService.tr("回调 JSON 自动修复成功"));
                    return result;
                } catch (Exception ignored) {
                    // 修复后仍然失败，走原始错误处理
                }
            }
            PluginLogger.warn("挂机任务", I18nService.tr("解析回调配置失败: {}", firstError.getMessage()));
            return new AFKTaskCallback();
        }
    }

    /**
     * 尝试修复不完整的 JSON（补全缺失的闭合花括号）
     * <p>
     * LLM 生成嵌套 JSON 时常见错误：缺少闭合 }，
     * 本方法统计 { 和 } 数量差，在末尾补全缺失的 }
     * </p>
     *
     * @param json 可能不完整的 JSON 字符串
     * @return 修复后的 JSON 字符串
     */
    private String repairJsonBraces(String json) {
        int open = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                if (inString) escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') open++;
            else if (c == '}') open--;
        }
        if (open > 0) {
            return json + "}".repeat(open);
        }
        return json;
    }

    /**
     * 获取回调配置
     *
     * @return 回调配置对象
     */
    protected AFKTaskCallback getCallback() {
        return callback;
    }

    /**
     * 判断是否配置了有效的回调任务
     *
     * @return true 如果配置了包含至少一个步骤的回调任务
     */
    protected boolean hasCallback() {
        return callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();
    }

    // ==================== 回调执行模板 ====================

    /**
     * 描述目标玩家：当目标玩家就是任务创建者时返回"你"，否则返回玩家名
     * <p>
     * 解决挂机任务中第三人称视角问题
     *
     * @param targetPlayerName 目标玩家名称
     * @return "你" 如果目标就是创建者，否则返回原始玩家名
     */
    protected String describeTarget(String targetPlayerName) {
        if (targetPlayerName != null && targetPlayerName.equalsIgnoreCase(getPlayerName())) {
            return I18nService.tr("你");
        }
        return targetPlayerName;
    }

    /**
     * 执行回调任务（无事件描述）
     *
     * @see #executeCallback(String, Consumer)
     */
    protected void executeCallback(Consumer<TaskPlan> placeholderReplacer) {
        executeCallback(null, placeholderReplacer);
    }

    /**
     * 执行回调任务（模板方法）
     *
     * <p>统一执行流程：</p>
     * <ol>
     *   <li>构建 TaskPlan</li>
     *   <li>调用子类提供的占位符替换逻辑</li>
     *   <li>获取任务创建者玩家对象</li>
     *   <li>构建执行上下文</li>
     *   <li>通过 TaskExecutor 执行多步骤任务</li>
     *   <li>LLM 二次分析结果并通知玩家</li>
     * </ol>
     *
     * @param eventDescription    事件触发描述，注入到 LLM 二次分析的 [执行结果] 区域
     * @param placeholderReplacer 子类提供的占位符替换逻辑，接收 TaskPlan 进行替换
     */
    protected void executeCallback(String eventDescription, Consumer<TaskPlan> placeholderReplacer) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            // 过滤末尾的 notify_player 步骤（与 AFK_CALLBACK 自动总结重复）
            stripTrailingNotifyPlayer(plan);
            placeholderReplacer.accept(plan);

            // 2. 获取任务创建者玩家对象
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                PluginLogger.warn("挂机任务", I18nService.tr("任务创建者不在线，无法执行回调: {}", getTaskId()));
                notifyPlayer("§c任务创建者不在线，回调任务已取消。");
                return;
            }

            // 3. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 4. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new ArrayDeque<>();

            // 5. 执行多步骤任务
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager());

            // userMessage 仅保留 goal，事件描述通过 injectEventTrigger 注入到 [执行结果] 区域
            String goal = callback.getCallbackTask().getGoal();

            CompletableFuture<AnalysisSummary> future = executor.executeTask(plan, context, history, goal);

            // 6. 处理执行结果：注入事件触发描述 + 通过中间层进行 LLM 二次分析并输出
            future.thenAccept(summary -> {
                summary.injectEventTrigger(eventDescription);
                plugin.getLlmOutputCoordinator().outputAnalysisResult(creatorPlayer, summary, context, history, OutputScenario.AFK_CALLBACK, false);
            }).exceptionally(ex -> {
                PluginLogger.error("挂机任务", I18nService.tr("回调任务执行异常: {}", ex.getMessage()), ex);
                Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
                if (errorPlayer != null && errorPlayer.isOnline()) {
                    plugin.getLlmOutputCoordinator().outputError(errorPlayer, I18nService.tr("§c回调任务执行失败：{}", ex.getMessage()));
                }
                return null;
            });
        } catch (Exception e) {
            PluginLogger.error("挂机任务", I18nService.tr("构建回调任务失败: {}", e.getMessage()), e);
            Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (errorPlayer != null && errorPlayer.isOnline()) {
                plugin.getLlmOutputCoordinator().outputError(errorPlayer, I18nService.tr("§c回调任务构建失败：{}", e.getMessage()));
            }
        }
    }

    // ==================== 并发防护 ====================

    /**
     * 尝试获取执行权（原子操作）
     *
     * <p>通过 CAS 操作确保回调只被执行一次，防止并发冲突。</p>
     *
     * @return true 如果成功获取执行权（第一次调用），false 如果已被其他线程执行
     */
    protected boolean tryAcquireExecution() {
        return callbackExecuted.compareAndSet(false, true);
    }

    // ==================== 监听器生命周期 ====================

    /**
     * 注册当前对象为事件监听器
     */
    protected void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listenerRegistered = true;
    }

    @Override
    protected void onStop() {
        if (listenerRegistered) {
            try {
                HandlerList.unregisterAll(this);
                listenerRegistered = false;
                PluginLogger.debug("挂机任务", "已停止: {}", getTaskId());
            } catch (Exception e) {
                PluginLogger.warn("挂机任务", I18nService.tr("注销事件监听器失败: {}", e.getMessage()), e);
            }
        }
    }

    // ==================== 参数解析工具 ====================

    /**
     * 从任务参数中获取 double 值
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 解析后的 double 值，解析失败返回默认值
     */
    protected double getParamDouble(String key, double defaultValue) {
        String value = getParam(key, "");
        if (value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
