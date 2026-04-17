package com.zm.kilacraftAI.skills.cmi;

import com.zm.kilacraftAI.compat.cmi.CMIAPI;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import com.zm.kilacraftAI.util.BukkitCommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class CMISkill implements Skill {

    private final SkillConfigManager configManager;

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
    public boolean isAvailable(SkillContext context) {
        return CMIAPI.isAvailable();
    }

    /**
     * 获取响应消息（从配置文件）
     */
    protected String getResponseMessage(String key, String defaultMessage) {
        SkillConfig config = getConfig();
        if (config != null && config.getResponseMessages() != null) {
            String message = config.getResponseMessages().get(key);
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return defaultMessage;
    }

    /**
     * 获取响应消息（支持变量替换）
     */
    protected String getResponseMessage(String key, String defaultMessage, Map<String, String> variables) {
        String message = getResponseMessage(key, defaultMessage);
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> e : variables.entrySet()) {
                message = message.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return message;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        try {
            String action = context.getAction();
            return actionToHandler.getOrDefault(action, this::handleUnknownAction).apply(context);
        } catch (Exception e) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("error", e.getMessage());
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("query_error", "查询失败：" + e.getMessage(), vars)));
        }
    }

    // 初始化动作映射
    private final Map<String, Function<SkillContext, CompletableFuture<SkillResult>>> actionToHandler = Map.ofEntries(entry("query_homes", this::queryHomes), entry("query_warps", this::queryWarps), entry("query_player_info", this::queryPlayerInfo), entry("query_kits", this::queryKits), entry("query_online_players", this::queryOnlinePlayers), entry("teleport_home", this::teleportHome), entry("teleport_to_warp", this::teleportToWarp), entry("send_tp_request", this::sendTpRequest));

    // ==================== 通用方法 ====================

    private CompletableFuture<SkillResult> handleUnknownAction(SkillContext context) {
        return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("unknown_action", "抱歉，我还不会这个CMI操作。你可以问我：'我的家'、'有哪些地标'、'谁在线'等")));
    }

    // ==================== 查询类动作（只读） ====================

    /**
     * 查询玩家的家列表
     */
    private CompletableFuture<SkillResult> queryHomes(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        List<Map<String, Object>> homes = CMIAPI.getHomes(player);

        if (homes.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("homes_empty", "§f你还没有设置任何家")));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getResponseMessage("homes_header", "§f你的家列表：\n"));
        String format = getResponseMessage("homes_format", "§7- §f家名：{name} §8| §7{world} §8(§7{x}, {y}, {z}§8)");

        List<Map<String, Object>> homesData = new ArrayList<>();
        for (Map<String, Object> home : homes) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("name", String.valueOf(home.get("name")));
            vars.put("world", String.valueOf(home.getOrDefault("world", "unknown")));
            vars.put("x", String.valueOf(home.getOrDefault("x", "?")));
            vars.put("y", String.valueOf(home.getOrDefault("y", "?")));
            vars.put("z", String.valueOf(home.getOrDefault("z", "?")));
            sb.append(format.replace("{name}", vars.get("name")).replace("{world}", vars.get("world")).replace("{x}", vars.get("x")).replace("{y}", vars.get("y")).replace("{z}", vars.get("z"))).append("\n");

            // 构建返回数据
            Map<String, Object> homeData = new LinkedHashMap<>();
            homeData.put("home_name", home.get("name"));
            homeData.put("world", home.getOrDefault("world", "unknown"));
            homeData.put("x", home.getOrDefault("x", 0));
            homeData.put("y", home.getOrDefault("y", 0));
            homeData.put("z", home.getOrDefault("z", 0));
            homesData.add(homeData);
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
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        List<Map<String, Object>> warps = CMIAPI.getWarps();

        if (warps.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("warps_empty", "§f服务器暂无公共地标")));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getResponseMessage("warps_header", "§f公共地标列表：\n"));
        String format = getResponseMessage("warps_format", "§7- §f地标名：{name} §8| §7{world} §8(§7{x}, {y}, {z}§8)");

        List<Map<String, Object>> warpsData = new ArrayList<>();
        for (Map<String, Object> warp : warps) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("name", String.valueOf(warp.get("name")));
            vars.put("world", String.valueOf(warp.getOrDefault("world", "unknown")));
            vars.put("x", String.valueOf(warp.getOrDefault("x", "?")));
            vars.put("y", String.valueOf(warp.getOrDefault("y", "?")));
            vars.put("z", String.valueOf(warp.getOrDefault("z", "?")));
            sb.append(format.replace("{name}", vars.get("name")).replace("{world}", vars.get("world")).replace("{x}", vars.get("x")).replace("{y}", vars.get("y")).replace("{z}", vars.get("z"))).append("\n");

            Map<String, Object> warpData = new LinkedHashMap<>();
            warpData.put("warp_name", warp.get("name"));
            warpData.put("world", warp.getOrDefault("world", "unknown"));
            warpData.put("x", warp.getOrDefault("x", 0));
            warpData.put("y", warp.getOrDefault("y", 0));
            warpData.put("z", warp.getOrDefault("z", 0));
            warpsData.add(warpData);
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
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        // 获取目标玩家名称（可选参数，默认查询自己）
        String targetName = context.getEntity("target_player");

        Map<String, Object> info;
        if (targetName != null && !targetName.isEmpty()) {
            info = CMIAPI.getOtherPlayerInfo(targetName);
            if (info == null) {
                Map<String, String> vars = new LinkedHashMap<>();
                vars.put("name", targetName);
                return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("player_not_found", "§f未找到玩家：{name}", vars)));
            }
        } else {
            info = CMIAPI.getPlayerInfo(player);
            if (info == null) {
                return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("cmi_not_available", "CMI 插件未安装，此功能不可用")));
            }
        }

        // 格式化输出
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", String.valueOf(info.get("name")));
        vars.put("display_name", String.valueOf(info.get("display_name")));
        vars.put("playtime", String.valueOf(info.get("playtime_formatted")));
        vars.put("afk", Boolean.TRUE.equals(info.get("afk")) ? "§a是" : "§c否");
        vars.put("vanished", Boolean.TRUE.equals(info.get("vanished")) ? "§a是" : "§c否");
        vars.put("fly", Boolean.TRUE.equals(info.get("fly")) ? "§a是" : "§c否");
        vars.put("game_mode", String.valueOf(info.get("game_mode")));

        String message = getResponseMessage("player_info_format", "§f玩家：§a{name}\n§f昵称：§a{display_name}\n§f游戏时长：§a{playtime}\n§fAFK：§a{afk}\n§f隐身：§a{vanished}\n§f飞行：§a{fly}\n§f游戏模式：§a{game_mode}", vars);

        return CompletableFuture.completedFuture(SkillResult.success(message, info));
    }


    /**
     * 查询可用套装列表
     */
    private CompletableFuture<SkillResult> queryKits(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        List<String> kits = CMIAPI.getKitNames();

        if (kits.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("kits_empty", "§f服务器暂无可用套装")));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getResponseMessage("kits_header", "§f可用套装列表：\n"));
        String format = getResponseMessage("kits_format", "§7- §f{name}");
        for (String kit : kits) {
            sb.append(format.replace("{name}", kit)).append("\n");
        }

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("kits", kits);
        dataMap.put("count", kits.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 查询在线玩家列表（CMI 增强版）
     */
    private CompletableFuture<SkillResult> queryOnlinePlayers(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_QUERY.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        List<Map<String, Object>> players = CMIAPI.getOnlinePlayersInfo();

        StringBuilder sb = new StringBuilder();
        Map<String, String> countVars = new LinkedHashMap<>();
        countVars.put("count", String.valueOf(players.size()));
        sb.append(getResponseMessage("online_header", "§f在线玩家 §a({count})§f：\n", countVars));

        String formatNormal = getResponseMessage("online_format", "§7- §f{name}");
        String formatAfk = getResponseMessage("online_format_afk", "§7- §f{name} §8[§eAFK§8]");
        String formatVanished = getResponseMessage("online_format_vanished", "§7- §f{name} §8[§5隐身§8]");
        String formatAfkVanished = getResponseMessage("online_format_afk_vanished", "§7- §f{name} §8[§eAFK§8]§8[§5隐身§8]");

        List<Map<String, Object>> playersData = new ArrayList<>();
        for (Map<String, Object> pInfo : players) {
            String name = String.valueOf(pInfo.get("name"));
            boolean afk = Boolean.TRUE.equals(pInfo.get("afk"));
            boolean vanished = Boolean.TRUE.equals(pInfo.get("vanished"));

            String line;
            if (afk && vanished) {
                line = formatAfkVanished.replace("{name}", name);
            } else if (afk) {
                line = formatAfk.replace("{name}", name);
            } else if (vanished) {
                line = formatVanished.replace("{name}", name);
            } else {
                line = formatNormal.replace("{name}", name);
            }
            sb.append(line).append("\n");
            playersData.add(pInfo);
        }

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("players", playersData);
        dataMap.put("count", players.size());

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    // ==================== 传送类动作（通过 dispatchCommand） ====================

    /**
     * 传送到自己的家
     */
    private CompletableFuture<SkillResult> teleportHome(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        String homeName = context.getEntity("home_name");
        if (homeName == null || homeName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_no_param", "请指定传送目标（家名称、地标名称或玩家名称）")));
        }

        // 验证家是否存在
        List<Map<String, Object>> homes = CMIAPI.getHomes(player);
        boolean found = homes.stream().anyMatch(h -> homeName.equalsIgnoreCase(String.valueOf(h.get("name"))));
        if (!found) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("home_name", homeName);
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_home_not_found", "§f未找到家：{home_name}", vars)));
        }

        // 通过 CMI 命令执行传送（受 CMI 权限、冷却、安全检查保护）
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi home " + homeName);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("home_name", homeName);
        return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("teleport_home_success", "§f已请求传送到家：§a{home_name}", vars)));
    }

    /**
     * 传送到公共地标
     */
    private CompletableFuture<SkillResult> teleportToWarp(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        String warpName = context.getEntity("warp_name");
        if (warpName == null || warpName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_no_param", "请指定传送目标（家名称、地标名称或玩家名称）")));
        }

        // 验证地标是否存在
        List<Map<String, Object>> warps = CMIAPI.getWarps();
        boolean found = warps.stream().anyMatch(w -> warpName.equalsIgnoreCase(String.valueOf(w.get("name"))));
        if (!found) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("warp_name", warpName);
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_warp_not_found", "§f未找到地标：{warp_name}", vars)));
        }

        // 通过 CMI 命令执行传送
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi warp " + warpName);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("warp_name", warpName);
        return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("teleport_warp_success", "§f已请求传送到地标：§a{warp_name}", vars)));
    }

    /**
     * 发送传送请求（TPA）
     */
    private CompletableFuture<SkillResult> sendTpRequest(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("not_player", "请在游戏中使用此功能")));
        }

        if (!PluginPermissionEnum.CMI_TELEPORT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("no_permission", "你没有权限使用此功能")));
        }

        String targetName = context.getEntity("target_player");
        if (targetName == null || targetName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_no_param", "请指定传送目标（家名称、地标名称或玩家名称）")));
        }

        // 验证目标玩家是否在线
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("target", targetName);
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("teleport_tpa_not_found", "§f未找到在线玩家：{target}", vars)));
        }

        // 通过 CMI 命令发送 TPA 请求（需要对方同意）
        BukkitCommandUtil.dispatchOnMainThread(player, "cmi tpa " + targetName);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("target", target.getName());
        return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("teleport_tpa_success", "§f已向 §a{target} §f发送传送请求，等待对方同意", vars)));
    }
}
