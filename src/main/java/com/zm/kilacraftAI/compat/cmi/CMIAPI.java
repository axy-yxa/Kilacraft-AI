package com.zm.kilacraftAI.compat.cmi;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import com.Zrips.CMI.Modules.Homes.CmiHome;
import com.Zrips.CMI.Modules.Warps.CmiWarp;
import com.zm.kilacraftAI.config.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CMI 插件 API 调用工具类
 *
 * <p>封装 CMI 插件的真实 API 调用，仅提供只读查询方法。</p>
 * <p>传送类操作通过 Bukkit.dispatchCommand() 调用 CMI 命令实现，不在此类中封装。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class CMIAPI {

    private CMIAPI() {
        // 工具类，禁止实例化
    }

    /**
     * 检查 CMI 是否已安装
     *
     * @return true=已安装，false=未安装
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("CMI") != null;
    }

    /**
     * 获取 CMI 主实例
     */
    private static CMI getCMI() {
        return CMI.getInstance();
    }

    /**
     * 获取 CMIUser
     *
     * @param player Bukkit Player
     * @return CMIUser，如果插件未安装则返回 null
     */
    public static CMIUser getUser(Player player) {
        if (!isAvailable() || player == null) return null;
        return getCMI().getPlayerManager().getUser(player);
    }

    // ==================== 家系统 ====================

    /**
     * 查询玩家的家列表
     *
     * @param player 玩家
     * @return 家列表信息，每个家包含 name、world、x、y、z
     */
    public static List<Map<String, Object>> getHomes(Player player) {
        CMIUser user = getUser(player);
        if (user == null) return new ArrayList<>();

        Map<String, CmiHome> homes = user.getHomes();
        if (homes == null || homes.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, CmiHome> entry : homes.entrySet()) {
            Map<String, Object> homeInfo = new HashMap<>();
            homeInfo.put("name", entry.getKey());
            CmiHome home = entry.getValue();
            if (home != null) {
                Location loc = home.getLoc();
                if (loc != null) {
                    homeInfo.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
                    homeInfo.put("x", loc.getBlockX());
                    homeInfo.put("y", loc.getBlockY());
                    homeInfo.put("z", loc.getBlockZ());
                }
            }
            result.add(homeInfo);
        }
        return result;
    }

    // ==================== 地标系统 ====================

    /**
     * 查询公共地标列表
     *
     * @return 地标列表信息，每个地标包含 name、world、x、y、z
     */
    public static List<Map<String, Object>> getWarps() {
        if (!isAvailable()) return new ArrayList<>();

        Map<String, CmiWarp> warps = getCMI().getWarpManager().getWarps();
        if (warps == null || warps.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, CmiWarp> entry : warps.entrySet()) {
            Map<String, Object> warpInfo = new HashMap<>();
            warpInfo.put("name", entry.getKey());
            CmiWarp warp = entry.getValue();
            if (warp != null) {
                Location loc = warp.getLoc();
                if (loc != null) {
                    warpInfo.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
                    warpInfo.put("x", loc.getBlockX());
                    warpInfo.put("y", loc.getBlockY());
                    warpInfo.put("z", loc.getBlockZ());
                }
            }
            result.add(warpInfo);
        }
        return result;
    }

    // ==================== 玩家信息 ====================

    /**
     * 查询 CMI 玩家增强信息
     *
     * @param player 玩家
     * @return 玩家信息 Map
     */
    public static Map<String, Object> getPlayerInfo(Player player) {
        CMIUser user = getUser(player);
        if (user == null) return null;

        Map<String, Object> info = new HashMap<>();
        info.put("name", user.getName());
        info.put("display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getName());
        info.put("afk", user.isAfk());
        info.put("vanished", user.isVanished());
        info.put("fly", user.isFlying());
        info.put("game_mode", player.getGameMode().toString());

        // 游戏时长（总在线时间，单位：毫秒）
        long playtime = user.getTotalPlayTime();
        info.put("playtime_ms", playtime);
        // 转换为可读格式
        long hours = playtime / 3600000;
        long minutes = (playtime % 3600000) / 60000;
        info.put("playtime_formatted", I18nService.tr("{}小时{}分钟", hours, minutes));

        return info;
    }


    // ==================== 套装系统 ====================

    /**
     * 查询可用套装列表
     *
     * @return 套装名称列表
     */
    public static List<String> getKitNames() {
        if (!isAvailable()) return new ArrayList<>();

        var kitsManager = getCMI().getKitsManager();
        if (kitsManager == null) return new ArrayList<>();

        // 获取所有套装名称
        var kitMap = kitsManager.getKitMap();
        if (kitMap == null) return new ArrayList<>();

        return new ArrayList<>(kitMap.keySet());
    }

    // ==================== 在线玩家增强 ====================

    /**
     * 查询在线玩家列表（CMI 增强版，含 AFK/隐身状态）
     *
     * @return 玩家信息列表
     */
    public static List<Map<String, Object>> getOnlinePlayersInfo() {
        if (!isAvailable()) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            CMIUser user = getUser(player);
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("name", player.getName());
            if (user != null) {
                playerInfo.put("afk", user.isAfk());
                playerInfo.put("vanished", user.isVanished());
            } else {
                playerInfo.put("afk", false);
                playerInfo.put("vanished", false);
            }
            result.add(playerInfo);
        }
        return result;
    }

    /**
     * 查询指定玩家的 CMI 增强信息（用于查看其他玩家）
     *
     * @param targetName 目标玩家名称
     * @return 玩家信息，如果玩家不在线或不存在返回 null
     */
    public static Map<String, Object> getOtherPlayerInfo(String targetName) {
        if (!isAvailable() || targetName == null) return null;

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) return null;

        return getPlayerInfo(target);
    }
}
