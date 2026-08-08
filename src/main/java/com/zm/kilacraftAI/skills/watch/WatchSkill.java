package com.zm.kilacraftAI.skills.watch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.watch.WatchService;
import com.zm.kilacraftAI.skills.framework.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家自定义监听技能：玩家通过自然语言创建条件监听（轮询型）或事件监听（事件型）。
 *
 * <p>轮询型：定时执行 skill action 取返回值字段比较判定（如盯血量低于某值）。
 * 事件型：监听 Bukkit 事件命中 filter 即触发（如盯熔炉烧好）。</p>
 *
 * <p>触发后只通知 AI（由 AI 决定如何回应），不做回调执行。
 * 监听完成写 server_event 存档，供问候系统回顾。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public class WatchSkill implements Skill, DynamicContextProvider {

    private static final String SKILL_NAME = "watch";
    private static final String LOG_PREFIX = "监听";

    private final SkillConfigManager configManager;

    public WatchSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return config != null ? config.getDescription() : "";
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null) ? new ArrayList<>(config.getHints()) : Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.WATCH.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        WatchService service = KilacraftAI.getInstance().getWatchService();
        return service != null;
    }

    @Override
    public String getDynamicContext(org.bukkit.entity.Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("【可监听列表（创建监听时从此选择）】\n"));

        // 轮询型：遍历当前玩家有权限使用的 skill 中实现 ProbeSource 的，列出可监听的只读查询 action
        // 用 getAvailableSkills 而非 getAllSkills，避免向无权限玩家暴露可监听 action
        sb.append(I18nService.tr("— 条件监听（定时轮询 skill action 取值比较）：\n"));
        SkillManager sm = KilacraftAI.getInstance().getSkillManager();
        if (sm != null) {
            for (Skill skill : sm.getAvailableSkills(player)) {
                if (!(skill instanceof ProbeSource ps)) continue;
                String skillName = skill.getName();
                Map<String, String> actions = skill.getActions();
                for (String action : ps.getProbeableActions()) {
                    sb.append("  - ").append(skillName).append(".").append(action);
                    String desc = actions != null ? actions.get(action) : null;
                    if (desc != null && !desc.isBlank()) {
                        sb.append(": ").append(desc);
                    }
                    sb.append("\n");
                }
            }
        }

        // 事件型：列出支持的 11 种事件
        sb.append(I18nService.tr("— 事件监听（Bukkit 事件命中即触发）：\n"));
        sb.append(I18nService.tr("  - furnace_smelt: 熔炉烧好（filter: result_type=产物材质）\n"));
        sb.append(I18nService.tr("  - crop_mature: 作物成熟（filter: crop_type=作物类型）\n"));
        sb.append(I18nService.tr("  - entity_death: 实体死亡（filter: entity_type=实体类型）\n"));
        sb.append(I18nService.tr("  - entity_spawn: 实体生成（filter: entity_type=实体类型）\n"));
        sb.append(I18nService.tr("  - player_death: 玩家死亡\n"));
        sb.append(I18nService.tr("  - player_teleport: 玩家传送（filter: cause=传送原因）\n"));
        sb.append(I18nService.tr("  - player_level_change: 经验等级变化\n"));
        sb.append(I18nService.tr("  - player_changed_world: 切换世界\n"));
        sb.append(I18nService.tr("  - block_break: 方块破坏（filter: block_type=方块类型）\n"));
        sb.append(I18nService.tr("  - player_fish: 钓鱼成功\n"));
        sb.append(I18nService.tr("  - player_chat: 聊天消息（filter: keyword=关键词，消息包含时触发）\n"));

        return sb.toString();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        org.bukkit.entity.Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }
        if (!PluginPermissionEnum.WATCH.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.WATCH.getNode())));
        }
        WatchService service = KilacraftAI.getInstance().getWatchService();
        if (service == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("监听系统未初始化")));
        }
        return CompletableFuture.completedFuture(switch (action) {
            case "create_watch" -> handleCreateWatch(service, player, context.getEntities());
            case "cancel_watch" -> {
                String watchId = SkillEntityHelper.getString(context.getEntities(), "watch_id");
                String description = SkillEntityHelper.getString(context.getEntities(), "description");
                yield service.cancelWatch(player.getUniqueId(), watchId, description);
            }
            case "list_watches" -> service.listWatches(player.getUniqueId());
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        });
    }

    private SkillResult handleCreateWatch(WatchService service, org.bukkit.entity.Player player, Map<String, String> entities) {
        String mode = SkillEntityHelper.getString(entities, "mode");
        if (mode == null) {
            return SkillResult.needInfo(I18nService.tr("要创建哪种监听？条件监听用 mode=polling（定时轮询取值比较），事件监听用 mode=event（事件命中即触发）。"));
        }
        String intent = SkillEntityHelper.getString(entities, "intent");
        boolean singleShot = SkillEntityHelper.getBoolean(entities, "single_shot", true);
        String displayName = SkillEntityHelper.getString(entities, "display_name");

        if ("polling".equalsIgnoreCase(mode)) {
            return handleCreatePollingWatch(service, player, entities, intent, singleShot, displayName);
        } else if ("event".equalsIgnoreCase(mode)) {
            return handleCreateEventWatch(service, player, entities, intent, singleShot, displayName);
        }
        return SkillResult.failure(I18nService.tr("不支持的监听模式：{}（可用: polling, event）", mode));
    }

    private SkillResult handleCreatePollingWatch(WatchService service, org.bukkit.entity.Player player, Map<String, String> entities, String intent, boolean singleShot, String displayName) {
        String source = SkillEntityHelper.getString(entities, "source");
        if (source == null) {
            return SkillResult.needInfo(I18nService.tr("要监听什么？请指定监听源，格式为 skill.action（如 player_status.get_player_health），可从【可监听列表】中选择。"));
        }
        // 解析 skill.action 格式
        int dotIdx = source.indexOf('.');
        if (dotIdx <= 0 || dotIdx >= source.length() - 1) {
            return SkillResult.failure(I18nService.tr("source 格式错误，应为 skill.action（如 player_status.get_player_health）"));
        }
        String skillName = source.substring(0, dotIdx);
        String actionName = source.substring(dotIdx + 1);

        String resultPath = SkillEntityHelper.getString(entities, "result_path");
        if (resultPath == null) {
            return SkillResult.needInfo(I18nService.tr("要比较 skill 返回值的哪个字段？请指定 result_path（如 health），可从该 action 的返回 data 字段描述中推断。"));
        }
        String operator = SkillEntityHelper.getString(entities, "operator");
        if (operator == null) {
            return SkillResult.needInfo(I18nService.tr("用什么方式比较？请指定 operator（数值用 greater_than/less_than/equal 等，字符串用 contains/equal 等）。"));
        }
        String threshold = SkillEntityHelper.getString(entities, "threshold");
        if (threshold == null) {
            return SkillResult.needInfo(I18nService.tr("阈值是多少？请指定 threshold（数值填数字，字符串填匹配值如 OBSIDIAN）。"));
        }

        // 收集 skill 执行参数（排除框架保留键）
        Map<String, String> params = new HashMap<>();
        for (var entry : entities.entrySet()) {
            String key = entry.getKey();
            if (!isReservedKey(key)) {
                params.put(key, entry.getValue());
            }
        }

        WatchService.CreateResult result = service.createPollingWatch(player, skillName, actionName, resultPath, params, operator, threshold, intent, singleShot, displayName);

        if (result.success()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("watch_id", result.watchId());
            data.put("display_name", displayName != null ? displayName : (skillName + "." + actionName));
            return SkillResult.success(result.message(), data);
        }
        PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("创建条件监听失败: {}", result.message()));
        return SkillResult.failure(result.message());
    }

    private SkillResult handleCreateEventWatch(WatchService service, org.bukkit.entity.Player player, Map<String, String> entities, String intent, boolean singleShot, String displayName) {
        String eventType = SkillEntityHelper.getString(entities, "event_type");
        if (eventType == null) {
            return SkillResult.needInfo(I18nService.tr("要监听哪种事件？请指定 event_type（如 furnace_smelt/crop_mature/player_death/block_break），可从【事件监听】列表中选择。"));
        }

        // 收集 filter 参数（filter_ 前缀的键）
        Map<String, String> filterParams = new HashMap<>();
        for (var entry : entities.entrySet()) {
            if (entry.getKey().startsWith("filter_")) {
                filterParams.put(entry.getKey().substring("filter_".length()), entry.getValue());
            }
        }

        WatchService.CreateResult result = service.createEventWatch(player, eventType, filterParams, intent, singleShot, displayName);

        if (result.success()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("watch_id", result.watchId());
            data.put("display_name", displayName != null ? displayName : eventType);
            return SkillResult.success(result.message(), data);
        }
        PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("创建事件监听失败: {}", result.message()));
        return SkillResult.failure(result.message());
    }

    /**
     * 框架保留键，不作为 skill 执行参数。
     */
    private static boolean isReservedKey(String key) {
        return key.equals("mode") || key.equals("source") || key.equals("result_path") || key.equals("operator") || key.equals("threshold") || key.equals("intent") || key.equals("single_shot") || key.equals("display_name") || key.equals("watch_id") || key.equals("description") || key.equals("action") || key.equals("event_type") || key.startsWith("filter_");
    }
}
