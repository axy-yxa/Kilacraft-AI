package com.zm.kilacraftAI.skills.cmi;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.BukkitCommandUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.cmi.CMIAPI;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CMI 插件技能
 *
 * <p>封装 CMI 插件功能，包括只读查询和传送操作。</p>
 * <p>传送操作通过 Bukkit.dispatchCommand() 调用 CMI 命令实现，确保权限控制由 CMI 处理。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class CMISkill implements Skill, ProbeSource {

    private static final String SKILL_NAME = "cmi";
    private static final String LOG_PREFIX = "CMI集成";

    private final SkillConfigManager configManager;

    private static final Set<String> PROBEABLE_ACTIONS = Set.of("query_homes", "query_warps", "query_player_info", "query_kits", "query_online_players");

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    public CMISkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig(this);
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
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
        return Collections.emptyMap();
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
    public String getRequiredPermission() {
        return PluginPermissionEnum.CMI_QUERY.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return CMIAPI.isAvailable();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        try {
            String action = context.getAction();
            if (action == null || action.isEmpty()) {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
            }
            return switch (action) {
                case "query_homes" -> queryHomes(context);
                case "query_warps" -> queryWarps(context);
                case "query_player_info" -> queryPlayerInfo(context);
                case "query_kits" -> queryKits(context);
                case "query_online_players" -> queryOnlinePlayers(context);
                case "teleport_home" -> teleportHome(context);
                case "teleport_to_warp" -> teleportToWarp(context);
                case "send_tp_request" -> sendTpRequest(context);
                default ->
                        CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
            };
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询失败: {}", e.getMessage()), e);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("查询失败: {}", e.getMessage())));
        }
    }

    /**
     * 查询玩家的家列表
     */
    private CompletableFuture<SkillResult> queryHomes(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_QUERY.getNode())));
        }

        List<Map<String, Object>> homes = CMIAPI.getHomes(player);

        if (homes.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("玩家没有设置任何家"), Map.of("homes", List.of(), "count", 0)));
        }

        List<Map<String, Object>> homesData = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("玩家有 {} 个家:", homes.size()));
        for (Map<String, Object> home : homes) {
            Map<String, Object> homeData = new LinkedHashMap<>();
            homeData.put("home_name", home.get("name"));
            homeData.put("world", home.getOrDefault("world", "unknown"));
            homeData.put("x", home.getOrDefault("x", 0));
            homeData.put("y", home.getOrDefault("y", 0));
            homeData.put("z", home.getOrDefault("z", 0));
            homesData.add(homeData);

            sb.append("\n  - ").append(home.get("name")).append(" (world=").append(home.getOrDefault("world", "unknown")).append(", x=").append(home.getOrDefault("x", "?")).append(", y=").append(home.getOrDefault("y", "?")).append(", z=").append(home.getOrDefault("z", "?")).append(")");
        }

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("homes", homesData);
        dataMap.put("count", homes.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 查询公共地标列表
     */
    private CompletableFuture<SkillResult> queryWarps(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_QUERY.getNode())));
        }

        List<Map<String, Object>> warps = CMIAPI.getWarps();

        if (warps.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("服务器暂无公共地标"), Map.of("warps", List.of(), "count", 0)));
        }

        List<Map<String, Object>> warpsData = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("服务器有 {} 个公共地标:", warps.size()));
        for (Map<String, Object> warp : warps) {
            Map<String, Object> warpData = new LinkedHashMap<>();
            warpData.put("warp_name", warp.get("name"));
            warpData.put("world", warp.getOrDefault("world", "unknown"));
            warpData.put("x", warp.getOrDefault("x", 0));
            warpData.put("y", warp.getOrDefault("y", 0));
            warpData.put("z", warp.getOrDefault("z", 0));
            warpsData.add(warpData);

            sb.append("\n  - ").append(warp.get("name")).append(" (world=").append(warp.getOrDefault("world", "unknown")).append(", x=").append(warp.getOrDefault("x", "?")).append(", y=").append(warp.getOrDefault("y", "?")).append(", z=").append(warp.getOrDefault("z", "?")).append(")");
        }

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("warps", warpsData);
        dataMap.put("count", warps.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 查询玩家 CMI 增强信息
     */
    private CompletableFuture<SkillResult> queryPlayerInfo(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_QUERY.getNode())));
        }

        Map<String, Object> info = CMIAPI.getPlayerInfo(player);
        if (info == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("CMI 插件未安装或不可用")));
        }

        // message 用语义化文案（playtime 格式化、game_mode 翻译），data（info）保持结构化
        long playtimeMs = ((Number) info.get("playtime_ms")).longValue();
        String playtimeText = I18nService.tr("{}小时{}分钟", playtimeMs / 3600000, (playtimeMs % 3600000) / 60000);
        String gameModeText = BukkitAPIResultFormatter.formatGameMode(player.getGameMode());
        String message = I18nService.tr("玩家信息: name={}, display_name={}, playtime={}, afk={}, vanished={}, fly={}, game_mode={}", info.get("name"), info.get("display_name"), playtimeText, info.get("afk"), info.get("vanished"), info.get("fly"), gameModeText);

        return CompletableFuture.completedFuture(SkillResult.success(message, info));
    }


    /**
     * 查询可用套装列表
     */
    private CompletableFuture<SkillResult> queryKits(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_QUERY.getNode())));
        }

        List<String> kits = CMIAPI.getKitNames();

        if (kits.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("服务器暂无可用套装"), Map.of("kits", List.of(), "count", 0)));
        }

        String message = I18nService.tr("可用套装列表 ({}): {}", kits.size(), String.join(", ", kits));

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("kits", kits);
        dataMap.put("count", kits.size());

        return CompletableFuture.completedFuture(SkillResult.success(message, dataMap));
    }

    /**
     * 查询在线玩家列表（CMI 增强版）
     */
    private CompletableFuture<SkillResult> queryOnlinePlayers(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_QUERY.getNode())));
        }

        List<Map<String, Object>> players = CMIAPI.getOnlinePlayersInfo();
        // 非 OP 查询者不返回隐身玩家（保护隐身），OP 可见全部（含隐身标记）
        boolean viewerIsOp = player.isOp();

        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> pInfo : players) {
            if (!viewerIsOp && Boolean.TRUE.equals(pInfo.get("vanished"))) {
                continue;
            }
            visible.add(pInfo);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("在线玩家 ({}):", visible.size()));

        List<Map<String, Object>> playersData = new ArrayList<>();
        for (Map<String, Object> pInfo : visible) {
            String name = String.valueOf(pInfo.get("name"));
            boolean afk = Boolean.TRUE.equals(pInfo.get("afk"));
            boolean vanished = Boolean.TRUE.equals(pInfo.get("vanished"));

            List<String> flags = new ArrayList<>();
            if (afk) flags.add("AFK");
            if (vanished) flags.add(I18nService.tr("隐身"));

            sb.append("\n  - ").append(name);
            if (!flags.isEmpty()) {
                sb.append(" [").append(String.join("|", flags)).append("]");
            }
            playersData.add(pInfo);
        }

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("players", playersData);
        dataMap.put("count", visible.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 传送到自己的家
     */
    private CompletableFuture<SkillResult> teleportHome(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_TELEPORT.getNode())));
        }

        String homeName = SkillEntityHelper.getString(context, "home_name");
        if (homeName == null || homeName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要传送到哪个家（家名称）")));
        }

        // 验证家是否存在
        List<Map<String, Object>> homes = CMIAPI.getHomes(player);
        boolean found = homes.stream().anyMatch(h -> homeName.equalsIgnoreCase(String.valueOf(h.get("name"))));
        if (!found) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未找到家: {}", homeName)));
        }

        // 通过 CMI 命令执行传送（受 CMI 权限、冷却、安全检查保护）
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi home " + homeName);

        return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("已请求传送到家: {}，传送结果由CMI处理", homeName)));
    }

    /**
     * 传送到公共地标
     */
    private CompletableFuture<SkillResult> teleportToWarp(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_TELEPORT.getNode())));
        }

        String warpName = SkillEntityHelper.getString(context, "warp_name");
        if (warpName == null || warpName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要传送到哪个地标（地标名称）")));
        }

        // 验证地标是否存在
        List<Map<String, Object>> warps = CMIAPI.getWarps();
        boolean found = warps.stream().anyMatch(w -> warpName.equalsIgnoreCase(String.valueOf(w.get("name"))));
        if (!found) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未找到地标: {}", warpName)));
        }

        // 通过 CMI 命令执行传送
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi warp " + warpName);

        return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("已请求传送到地标: {}，传送结果由CMI处理", warpName)));
    }

    /**
     * 发送传送请求（TPA）
     */
    private CompletableFuture<SkillResult> sendTpRequest(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.CMI_TELEPORT.getNode())));
        }

        String targetName = SkillEntityHelper.getString(context, "target_player");
        if (targetName == null || targetName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要向哪位玩家发送传送请求")));
        }

        // 验证目标玩家是否在线
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未找到在线玩家: {}", targetName)));
        }

        // 通过 CMI 命令发送 TPA 请求（需要对方同意）
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi tpa " + targetName);

        return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("已向 {} 发送传送请求(TPA)，等待对方同意", target.getName())));
    }
}
