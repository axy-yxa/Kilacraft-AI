package com.zm.kilacraftAI.skills.afktask;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.skills.afktask.impl.*;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 挂机任务内置技能
 *
 * <p>提供挂机任务的创建、取消和查询能力，通过 LLM 意图识别自动路由。</p>
 *
 * <h3>动作列表：</h3>
 * <ul>
 *   <li>create_task - 创建挂机任务</li>
 *   <li>cancel_task - 取消挂机任务</li>
 *   <li>query_task - 查询当前挂机任务状态</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class AFKTaskSkill implements Skill {

    private final SkillConfigManager configManager;

    public AFKTaskSkill() {
        // 获取配置管理器实例
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("afktask", "AFKTaskSkill") == null) {
            // 保存默认配置到磁盘
            configManager.saveDefaultSkillConfig("afktask", "AFKTaskSkill");
            // 从磁盘动态加载配置到内存
            configManager.loadSingleSkillConfig("afktask", "AFKTaskSkill");
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("afktask", "AFKTaskSkill");
    }

    @Override
    public String getName() {
        return "AFKTask";
    }

    @Override
    public String getDescription() {
        // 优先使用配置文件中的描述，如果没有则使用默认值
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return java.util.Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return new ArrayList<>();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        if (action == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("未指定挂机任务动作。请用自然语言询问玩家想做什么"));
        }

        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("挂机任务仅支持在线玩家使用"));
        }

        KilacraftAI plugin = KilacraftAI.getInstance();
        if (!plugin.getConfigManager().isAfkTaskEnabled()) {
            return CompletableFuture.completedFuture(SkillResult.failure("挂机任务功能当前未启用"));
        }

        AFKTaskManager manager = plugin.getAfkTaskManager();
        if (manager == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("挂机任务系统未初始化"));
        }

        SkillResult result = switch (action) {
            case "create_task" -> handleCreateTask(context, player, manager);
            case "cancel_task" -> manager.cancelTask(player.getUniqueId());
            case "query_task" -> handleQueryTask(player, manager);
            default ->
                    SkillResult.failure(I18nService.tr("未知的挂机任务动作：{}。请用自然语言告知玩家操作无法识别，并询问具体需求", action));
        };

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 处理创建挂机任务
     */
    private SkillResult handleCreateTask(SkillContext context, Player player, AFKTaskManager manager) {
        String taskTypeStr = context.getEntity("task_type");
        String description = context.getEntity("description");

        if (taskTypeStr == null || taskTypeStr.isEmpty()) {
            return SkillResult.failure("缺少 task_type 参数。请用自然语言询问玩家想监视什么（上线还是下线）");
        }

        AFKTaskType taskType = AFKTaskType.fromActionName(taskTypeStr);

        // 统计埋点：记录挂机任务类型
        MetricsCollector.getInstance().recordAfkTaskType(taskType.name());

        // 检查是否已存在任务
        if (manager.hasTask(player.getUniqueId())) {
            AFKTask existingTask = manager.getTask(player.getUniqueId());
            return SkillResult.failure(I18nService.tr("玩家已有一个正在运行的挂机任务（{}）。请用自然语言告知玩家当前有任务在运行，并建议使用 /kilacraft afk cancel 取消旧任务后再创建新的", existingTask.getTaskDescription()));
        }

        // 目标在线/离线合理性检查
        String targetPlayerName = context.getEntity("target_player");
        if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
            Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
            boolean isOnline = targetPlayer != null && targetPlayer.isOnline();

            if (taskType == AFKTaskType.PLAYER_ONLINE_WATCH && isOnline) {
                return SkillResult.failure(I18nService.tr("目标玩家 {} 当前已在线，PLAYER_ONLINE_WATCH 任务无意义。请用自然语言告知玩家目标已在线，并建议：如果需要监视 TA 下线，可以说\"帮我盯着{}下线\"", targetPlayerName, targetPlayerName));
            }
            if (taskType == AFKTaskType.PLAYER_OFFLINE_WATCH && !isOnline) {
                return SkillResult.failure(I18nService.tr("目标玩家 {} 当前不在线，PLAYER_OFFLINE_WATCH 任务无意义。请用自然语言告知玩家目标不在线，并建议：如果需要监视 TA 上线，可以说\"帮我盯着{}上线\"", targetPlayerName, targetPlayerName));
            }
        }

        // 安全限制：涉及玩家隐私的任务类型，仅允许监控创建者自身
        if (isPlayerPrivacyType(taskType)) {
            if (targetPlayerName != null && !targetPlayerName.isEmpty() && !targetPlayerName.equalsIgnoreCase(player.getName())) {
                return SkillResult.failure("安全限制：该任务类型仅支持监控自身数据，不支持监控其他玩家。请用自然语言告知玩家此限制");
            }
        }

        // 收集所有 entities 作为任务参数
        Map<String, String> params = new HashMap<>(context.getEntities());
        // 根据任务类型选择工厂
        AFKTaskManager.AFKTaskFactory factory = getTaskFactory(taskType);
        return manager.createTask(player, taskType, description, params, factory);
    }

    /**
     * 获取任务类型的工厂
     *
     * <p>根据任务类型返回对应的 AFKTask 工厂实现。</p>
     */
    private AFKTaskManager.AFKTaskFactory getTaskFactory(AFKTaskType taskType) {
        return switch (taskType) {
            case PLAYER_ONLINE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerOnlineWatchTask(id, uuid, name, desc, p);
            case PLAYER_OFFLINE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerOfflineWatchTask(id, uuid, name, desc, p);
            case PLAYER_DEATH_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerDeathWatchTask(id, uuid, name, desc, p);
            case PLAYER_TELEPORT_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerTeleportWatchTask(id, uuid, name, desc, p);
            case PLAYER_LEVEL_CHANGE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerLevelChangeWatchTask(id, uuid, name, desc, p);
            case PLAYER_CHANGED_WORLD_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerChangedWorldWatchTask(id, uuid, name, desc, p);
            case WEATHER_CHANGE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new WeatherChangeWatchTask(id, uuid, name, desc, p);
            case PLAYER_BED_ENTER_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerBedEnterWatchTask(id, uuid, name, desc, p);
            case PLAYER_BED_LEAVE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerBedLeaveWatchTask(id, uuid, name, desc, p);
            case PLAYER_RESPAWN_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerRespawnWatchTask(id, uuid, name, desc, p);
            case PLAYER_ITEM_BREAK_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerItemBreakWatchTask(id, uuid, name, desc, p);
            case PLAYER_FISH_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerFishWatchTask(id, uuid, name, desc, p);
            case PLAYER_CHAT_WATCH ->
                    (id, uuid, name, type, desc, p) -> new PlayerChatWatchTask(id, uuid, name, desc, p);
            case BLOCK_BREAK_WATCH ->
                    (id, uuid, name, type, desc, p) -> new BlockBreakWatchTask(id, uuid, name, desc, p);
            case ENTITY_DEATH_WATCH ->
                    (id, uuid, name, type, desc, p) -> new EntityDeathWatchTask(id, uuid, name, desc, p);
            case ENTITY_SPAWN_WATCH ->
                    (id, uuid, name, type, desc, p) -> new EntitySpawnWatchTask(id, uuid, name, desc, p);
            case ENTITY_EXPLODE_WATCH ->
                    (id, uuid, name, type, desc, p) -> new EntityExplodeWatchTask(id, uuid, name, desc, p);
            case FURNACE_SMELT_WATCH ->
                    (id, uuid, name, type, desc, p) -> new FurnaceSmeltWatchTask(id, uuid, name, desc, p);
            case BLOCK_GROW_WATCH -> (id, uuid, name, type, desc, p) -> new BlockGrowWatchTask(id, uuid, name, desc, p);
            case CUSTOM -> (id, uuid, name, type, desc, p) -> new CustomWatchTask(id, uuid, name, desc, p);
        };
    }

    /**
     * 判断任务类型是否涉及玩家隐私数据（需要跨玩家权限才能监控他人）
     *
     * <p>采用白名单制：只有明确允许跨玩家监控的类型才返回 false，
     * 其余所有类型默认视为涉及隐私，仅允许监控创建者自身。</p>
     *
     * <p>当前允许跨玩家的类型：PLAYER_ONLINE_WATCH、PLAYER_OFFLINE_WATCH
     * （在线/下线状态不属于隐私数据）。</p>
     */
    private boolean isPlayerPrivacyType(AFKTaskType taskType) {
        return switch (taskType) {
            case PLAYER_ONLINE_WATCH, PLAYER_OFFLINE_WATCH -> false;
            default -> true;
        };
    }

    /**
     * 处理查询挂机任务
     */
    private SkillResult handleQueryTask(Player player, AFKTaskManager manager) {
        UUID playerUUID = player.getUniqueId();
        if (!manager.hasTask(playerUUID)) {
            return SkillResult.success("玩家当前没有正在运行的挂机任务。请用自然语言告知玩家");
        }

        AFKTask task = manager.getTask(playerUUID);
        String taskInfo = I18nService.tr("玩家当前挂机任务信息：任务ID={}, 类型={}, 描述={}, 状态={}, 创建时间={}。请基于这些信息用自然语言告知玩家当前任务状态", task.getTaskId(), task.getTaskType().getLocalizedDescription(), task.getTaskDescription(), task.getStatusText(), formatTimestamp(task.getCreatedAt()));
        return SkillResult.success(taskInfo);
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
}
