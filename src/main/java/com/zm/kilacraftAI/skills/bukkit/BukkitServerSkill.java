package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务器信息查询 Skill（server 域，6 个 action）
 *
 * <p>承载原 {@code GenericBukkitAPISkill} 中权限 {@code kilacraft.api.server.info} 下的全部 API：
 * 在线玩家列表、最大玩家数、服务器版本、MOTD、世界列表、服务器设置。
 * 入参/反参/字段名/线程模型逐项沿用原实现，零行为回归。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public class BukkitServerSkill extends AbstractBukkitQuerySkill {

    private static final String SKILL_NAME = "bukkit_server";
    private static final String LOG_PREFIX = "Bukkit服务器查询";

    /**
     * ProbeSource 只读 action 集合：排除 get_server_motd（返回非结构化展示文本，不适合监听）。
     */
    private static final Set<String> PROBEABLE_ACTIONS = Set.of(
            "get_online_players", "get_max_players", "get_server_version",
            "get_server_worlds", "get_server_settings");

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    protected String getLogPrefix() {
        return LOG_PREFIX;
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.API_SERVER_INFO.getNode();
    }

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    @Override
    protected SkillResult executeActions(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case "get_online_players" -> getOnlinePlayers(player);
            case "get_max_players" -> getMaxPlayers(player);
            case "get_server_version" -> getServerVersion(player);
            case "get_server_motd" -> getServerMotd(player);
            case "get_server_worlds" -> getServerWorlds(player);
            case "get_server_settings" -> getServerSettings(player);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 在线玩家列表（method_chain: getOnlinePlayers，data_field: online_count，Collection 特殊注入 players）
     */
    private SkillResult getOnlinePlayers(Player player) {
        Server server = player.getServer();
        Collection<? extends Player> players = server.getOnlinePlayers();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", players);
        dataMap.put("api_id", "get_online_players");
        // 原 data_field Collection 注入：online_count = size；Player 集合额外注入 players（逗号分隔名）
        dataMap.put("online_count", players.size());
        String playerNames = players.stream().map(Player::getName).collect(Collectors.joining(", "));
        dataMap.put("players", playerNames);
        // 原 formatResult：Collection → formatCollection → formatPlayerCollection
        String message = BukkitAPIResultFormatter.formatPlayerCollection(players);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 最大玩家数（method_chain: getMaxPlayers，data_field: max_players，数值无前缀）
     */
    private SkillResult getMaxPlayers(Player player) {
        int max = player.getServer().getMaxPlayers();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", max);
        dataMap.put("api_id", "get_max_players");
        dataMap.put("max_players", max);
        // 原 formatResult：Integer 非 ping/exp_to_level/arrows → 默认 toString
        String message = String.valueOf(max);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 服务器版本（additional_methods: getVersion/getBukkitVersion，模板）
     */
    private SkillResult getServerVersion(Player player) {
        Server server = player.getServer();
        String version = server.getVersion();
        String bukkitVersion = server.getBukkitVersion();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("version", version);
        rawMap.put("bukkit_version", bukkitVersion);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_server_version");
        dataMap.put("version", version);
        dataMap.put("bukkit_version", bukkitVersion);
        // 模板 "服务器版本：{version}, Bukkit 版本：{bukkit_version}"，均为 String 直接替换
        String message = I18nService.tr("服务器版本：{}, Bukkit 版本：{}", version, bukkitVersion);
        return SkillResult.success(message, dataMap);
    }

    /**
     * MOTD（method_chain: getMotd，字符串无前缀，仅 raw_result，不进 ProbeSource）
     */
    private SkillResult getServerMotd(Player player) {
        String motd = player.getServer().getMotd();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", motd);
        dataMap.put("api_id", "get_server_motd");
        // 原 formatResult：String 非 biome → 默认 toString
        String message = String.valueOf(motd);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界列表（method_chain: getWorlds，data_field: world_count，Collection→size 注入，formatWorldCollection）
     */
    private SkillResult getServerWorlds(Player player) {
        var worlds = player.getServer().getWorlds();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", worlds);
        dataMap.put("api_id", "get_server_worlds");
        // 原 data_field Collection 注入：world_count = size
        dataMap.put("world_count", worlds.size());
        // 原 formatResult：Collection → formatCollection → formatWorldCollection
        String message = BukkitAPIResultFormatter.formatWorldCollection(worlds);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 服务器设置（additional_methods: getAllowFlight/getAllowNether/getAllowEnd，模板）
     */
    private SkillResult getServerSettings(Player player) {
        Server server = player.getServer();
        boolean allowFlight = server.getAllowFlight();
        boolean allowNether = server.getAllowNether();
        boolean allowEnd = server.getAllowEnd();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("allow_flight", allowFlight);
        rawMap.put("allow_nether", allowNether);
        rawMap.put("allow_end", allowEnd);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_server_settings");
        dataMap.put("allow_flight", allowFlight);
        dataMap.put("allow_nether", allowNether);
        dataMap.put("allow_end", allowEnd);
        String message = I18nService.tr("允许飞行：{}, 允许下界：{}, 允许末地：{}", BukkitAPIResultFormatter.formatBoolean(allowFlight), BukkitAPIResultFormatter.formatBoolean(allowNether), BukkitAPIResultFormatter.formatBoolean(allowEnd));
        return SkillResult.success(message, dataMap);
    }
}
