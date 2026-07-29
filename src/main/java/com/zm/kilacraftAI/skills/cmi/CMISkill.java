package com.zm.kilacraftAI.skills.cmi;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.BukkitCommandUtil;
import com.zm.kilacraftAI.compat.cmi.CMIAPI;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Map.entry;

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

    private final SkillConfigManager configManager;

    private static final Set<String> PROBEABLE_ACTIONS = Set.of("query_homes", "query_warps", "query_player_info", "query_kits", "query_online_players");

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    public CMISkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("cmi", "CMISkill") == null) {
            configManager.saveDefaultSkillConfig("cmi", "CMISkill");
            configManager.loadSingleSkillConfig("cmi", "CMISkill");
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("cmi", "CMISkill");
    }

    @Override
    public String getName() {
        return "cmi";
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
        try {
            String action = context.getAction();
            return actionToHandler.getOrDefault(action, this::handleUnknownAction).apply(context);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("查询失败: {}", e.getMessage())));
        }
    }

    // 初始化动作映射
    private final Map<String, Function<SkillContext, CompletableFuture<SkillResult>>> actionToHandler = Map.ofEntries(entry("query_homes", this::queryHomes), entry("query_warps", this::queryWarps), entry("query_player_info", this::queryPlayerInfo), entry("query_kits", this::queryKits), entry("query_online_players", this::queryOnlinePlayers), entry("teleport_home", this::teleportHome), entry("teleport_to_warp", this::teleportToWarp), entry("send_tp_request", this::sendTpRequest));

    private CompletableFuture<SkillResult> handleUnknownAction(SkillContext context) {
        return CompletableFuture.completedFuture(SkillResult.failure("不支持的CMI操作，可用的操作：query_homes, query_warps, query_player_info, query_kits, query_online_players, teleport_home, teleport_to_warp, send_tp_request"));
    }

    /**
     * 查询玩家的家列表
     */
    private CompletableFuture<SkillResult> queryHomes(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.query 权限"));
        }

        List<Map<String, Object>> homes = CMIAPI.getHomes(player);

        if (homes.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("玩家没有设置任何家", Map.of("homes", List.of(), "count", 0)));
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
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.query 权限"));
        }

        List<Map<String, Object>> warps = CMIAPI.getWarps();

        if (warps.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("服务器暂无公共地标", Map.of("warps", List.of(), "count", 0)));
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
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.query 权限"));
        }

        String targetName = context.getEntity("target_player");

        Map<String, Object> info;
        if (targetName != null && !targetName.isEmpty()) {
            info = CMIAPI.getOtherPlayerInfo(targetName);
            if (info == null) {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未找到玩家: {}", targetName)));
            }
        } else {
            info = CMIAPI.getPlayerInfo(player);
            if (info == null) {
                return CompletableFuture.completedFuture(SkillResult.failure("CMI 插件未安装或不可用"));
            }
        }

        String message = I18nService.tr("玩家信息: name={}, display_name={}, playtime={}, afk={}, vanished={}, fly={}, game_mode={}", info.get("name"), info.get("display_name"), info.get("playtime_formatted"), info.get("afk"), info.get("vanished"), info.get("fly"), info.get("game_mode"));

        return CompletableFuture.completedFuture(SkillResult.success(message, info));
    }


    /**
     * 查询可用套装列表
     */
    private CompletableFuture<SkillResult> queryKits(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.query 权限"));
        }

        List<String> kits = CMIAPI.getKitNames();

        if (kits.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("服务器暂无可用套装", Map.of("kits", List.of(), "count", 0)));
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
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.query 权限"));
        }

        List<Map<String, Object>> players = CMIAPI.getOnlinePlayersInfo();

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("在线玩家 ({}):", players.size()));

        List<Map<String, Object>> playersData = new ArrayList<>();
        for (Map<String, Object> pInfo : players) {
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
        dataMap.put("count", players.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 传送到自己的家
     */
    private CompletableFuture<SkillResult> teleportHome(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.teleport 权限"));
        }

        String homeName = context.getEntity("home_name");
        if (homeName == null || homeName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少参数: home_name(家名称)"));
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
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.teleport 权限"));
        }

        String warpName = context.getEntity("warp_name");
        if (warpName == null || warpName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少参数: warp_name(地标名称)"));
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
            return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure("权限不足: 缺少 kilacraft.cmi.teleport 权限"));
        }

        String targetName = context.getEntity("target_player");
        if (targetName == null || targetName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少参数: target_player(目标玩家名称)"));
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
