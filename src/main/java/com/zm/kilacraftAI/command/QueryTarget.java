package com.zm.kilacraftAI.command;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.util.UUID;

/**
 * 查询类命令（usage/history/memory）的目标解析：按权限区分自己 / 指定玩家 / 全服。
 * self 路径（无参）需 selfPerm；指定玩家名或 all 需 otherPerm。
 * 在线玩家 UUID 同步可得；离线玩家 UUID 留给调用方在异步块用 resolveOfflineUuid 反查。
 *
 * @author Zm_Mmm
 * @since 2026-06-25
 */
public final class QueryTarget {

    private QueryTarget() {
    }

    /**
     * 解析结果。global=true 表示全服聚合（uuid/displayName 为 null）。
     * uuid 为 null 且 displayName 非空时表示命名的离线玩家，UUID 需异步反查。
     */
    public record Resolved(boolean global, UUID uuid, String displayName) {
    }

    /**
     * 同步解析目标模式与权限校验。
     *
     * @param sender    命令发送者
     * @param arg       目标参数（玩家名 / all / null=自己）
     * @param selfPerm  自查看权限
     * @param otherPerm 查看他人或全服的权限
     * @param allowAll  是否允许 all 关键字（usage 允许，history/memory 不允许）
     * @return 返回解析结果；权限不足或控制台缺参时返回 null（已向 sender 发送提示）
     */
    public static Resolved resolve(CommandSender sender, String arg, PluginPermissionEnum selfPerm, PluginPermissionEnum otherPerm, boolean allowAll) {
        if (arg == null || arg.isEmpty()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(I18nService.tr("§c控制台需指定玩家名或 all。"));
                return null;
            }
            if (!selfPerm.hasPermission(sender)) {
                sender.sendMessage(I18nService.tr("§c你没有权限查看自己的数据。"));
                return null;
            }
            return new Resolved(false, player.getUniqueId(), player.getName());
        }

        if (!otherPerm.hasPermission(sender)) {
            sender.sendMessage(I18nService.tr("§c你没有权限查看其他玩家的数据。"));
            return null;
        }
        if (allowAll && isAllKeyword(arg)) {
            return new Resolved(true, null, null);
        }
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) {
            return new Resolved(false, online.getUniqueId(), online.getName());
        }
        // 离线玩家：UUID 留给调用方在异步块反查
        return new Resolved(false, null, arg);
    }

    /**
     * 离线玩家 UUID 反查（在异步 DB 块中调用）。
     * 返回玩家 UUID；未找到或查询异常返回 null。
     */
    public static UUID resolveOfflineUuid(Connection conn, String tablePrefix, String name) {
        try {
            var profile = new PlayerProfileDao(tablePrefix).loadByName(conn, name);
            return profile != null ? profile.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isAllKeyword(String arg) {
        String lower = arg.toLowerCase();
        return "all".equals(lower) || "global".equals(lower) || "server".equals(lower);
    }
}
