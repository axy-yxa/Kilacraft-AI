package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.command.QueryTarget;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ConversationDao;
import com.zm.kilacraftAI.db.dao.SkillLogDao;
import com.zm.kilacraftAI.db.dao.SkillLogDao.PlayerUsageStat;
import com.zm.kilacraftAI.db.dao.SkillLogDao.SkillUsageStat;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * /kila usage [all|玩家名] [时间范围]：AI 用量统计（双维度，query.self / usage.other）。
 * 纯只读聚合现有表（kca_skill_log + kca_conversation），不落盘。口径为活跃度
 * （对话轮数 + 技能调用数），非费用——主对话链路不采集 token。默认时间范围 7d。
 *
 * @author Zm_Mmm
 * @since 2026-06-25
 */
public final class UsageCommand {

    private static final int TOP_LIMIT = 5;

    private UsageCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null) {
            sender.sendMessage(lm.getCommandUsageNotInit());
            return;
        }

        // args[1] 可为目标（all/玩家名）或时间范围（1d/3d/7d/30d）；[after,before] 在同步阶段预解析为非 null，避免异步块空指针
        String targetArg = null;
        long[] range = AdminSkillUtil.parseTimeRangeFull("7d");
        String timeRange = "7d";
        if (args.length >= 2) {
            long[] r1 = AdminSkillUtil.parseTimeRangeFull(args[1]);
            if (r1 != null) {
                range = r1;
                timeRange = args[1];
            } else {
                targetArg = args[1];
                if (args.length >= 3) {
                    long[] r2 = AdminSkillUtil.parseTimeRangeFull(args[2]);
                    if (r2 != null) {
                        range = r2;
                        timeRange = args[2];
                    }
                }
            }
        }
        final long[] finalRange = range != null ? range : new long[]{0, System.currentTimeMillis()};
        final String finalTimeRange = timeRange;

        QueryTarget.Resolved target = QueryTarget.resolve(sender, targetArg, PluginPermissionEnum.QUERY_SELF, PluginPermissionEnum.USAGE_OTHER, true);
        if (target == null) return;

        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = dbManager.getConnection()) {
                String prefix = dbManager.getTablePrefix();
                List<String> lines = target.global() ? buildGlobalLines(conn, prefix, finalRange[0], finalRange[1], finalTimeRange, lm) : buildPlayerLines(conn, prefix, target, finalRange[0], finalRange[1], finalTimeRange, lm);
                FoliaCompat.runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (Exception e) {
                PluginLoggerUtil.error("用量", I18nService.tr("查询用量统计失败: {}", e.getMessage()), e);
                FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandUsageQueryFailed()));
            }
        });
    }

    /**
     * 单玩家视图：对话轮数 + 技能调用分解（按技能/动作 Top）。
     * 离线玩家 UUID 在此异步块反查。
     */
    public static List<String> buildPlayerLines(Connection conn, String prefix, QueryTarget.Resolved target, long after, long before, String timeRange, LanguageManager lm) throws SQLException {
        UUID uuid = target.uuid();
        if (uuid == null) {
            uuid = QueryTarget.resolveOfflineUuid(conn, prefix, target.displayName());
            if (uuid == null) {
                List<String> lines = new ArrayList<>();
                lines.add(lm.replacePlaceholders(lm.getCommandUsagePlayerNotFound(), "player", target.displayName()));
                return lines;
            }
        }
        ConversationDao convDao = new ConversationDao(prefix);
        SkillLogDao skillDao = new SkillLogDao(prefix);
        int turns = convDao.countUserTurns(conn, uuid.toString(), after, before);
        List<SkillUsageStat> stats = skillDao.queryUsageByPlayer(conn, uuid.toString(), after, before, TOP_LIMIT);
        int skillTotal = stats.stream().mapToInt(SkillUsageStat::totalCount).sum();
        int skillOk = stats.stream().mapToInt(SkillUsageStat::successCount).sum();
        double rate = skillTotal > 0 ? Math.round((double) skillOk / skillTotal * 1000.0) / 10.0 : 0.0;

        List<String> lines = new ArrayList<>();
        lines.add(lm.replacePlaceholders(lm.getCommandUsageTitle(), "player", target.displayName(), "range", timeRange));
        lines.add(lm.replacePlaceholders(lm.getCommandUsageTurns(), "value", String.valueOf(turns)));
        lines.add(lm.replacePlaceholders(lm.getCommandUsageSkills(), "total", String.valueOf(skillTotal), "ok", String.valueOf(skillOk), "fail", String.valueOf(skillTotal - skillOk), "rate", String.valueOf(rate)));
        if (!stats.isEmpty()) {
            lines.add(lm.getCommandUsageTopSkills());
            for (SkillUsageStat s : stats) {
                lines.add(lm.replacePlaceholders(lm.getCommandUsageSkillLine(), "skill", s.skillName(), "action", s.action(), "count", String.valueOf(s.totalCount())));
            }
        }
        lines.add(lm.getCommandUsageScope());
        return lines;
    }

    /**
     * 全服视图：总对话轮 + 总技能调用（含成功率）+ 活跃玩家 Top + Top 技能。
     */
    public static List<String> buildGlobalLines(Connection conn, String prefix, long after, long before, String timeRange, LanguageManager lm) throws SQLException {
        ConversationDao convDao = new ConversationDao(prefix);
        SkillLogDao skillDao = new SkillLogDao(prefix);
        int totalTurns = convDao.countUserTurns(conn, null, after, before);
        List<SkillUsageStat> stats = skillDao.queryUsageStats(conn, after, before, 10);
        List<PlayerUsageStat> topPlayers = skillDao.queryTopPlayers(conn, after, before, TOP_LIMIT);
        int skillTotal = stats.stream().mapToInt(SkillUsageStat::totalCount).sum();
        int skillOk = stats.stream().mapToInt(SkillUsageStat::successCount).sum();
        double rate = skillTotal > 0 ? Math.round((double) skillOk / skillTotal * 1000.0) / 10.0 : 0.0;

        List<String> lines = new ArrayList<>();
        lines.add(lm.replacePlaceholders(lm.getCommandUsageGlobalTitle(), "range", timeRange));
        lines.add(lm.replacePlaceholders(lm.getCommandUsageTurns(), "value", String.valueOf(totalTurns)));
        lines.add(lm.replacePlaceholders(lm.getCommandUsageSkills(), "total", String.valueOf(skillTotal), "ok", String.valueOf(skillOk), "fail", String.valueOf(skillTotal - skillOk), "rate", String.valueOf(rate)));
        if (!topPlayers.isEmpty()) {
            lines.add(lm.getCommandUsageActivePlayers());
            Map<String, String> nameCache = new HashMap<>();
            for (PlayerUsageStat p : topPlayers) {
                String name = AdminSkillUtil.resolvePlayerName(p.playerUuid(), conn, nameCache);
                lines.add(lm.replacePlaceholders(lm.getCommandUsagePlayerLine(), "name", name, "count", String.valueOf(p.totalCount())));
            }
        }
        if (!stats.isEmpty()) {
            lines.add(lm.getCommandUsageTopSkills());
            int shown = 0;
            for (SkillUsageStat s : stats) {
                if (shown++ >= TOP_LIMIT) break;
                lines.add(lm.replacePlaceholders(lm.getCommandUsageSkillLine(), "skill", s.skillName(), "action", s.action(), "count", String.valueOf(s.totalCount())));
            }
        }
        lines.add(lm.getCommandUsageScope());
        return lines;
    }
}
