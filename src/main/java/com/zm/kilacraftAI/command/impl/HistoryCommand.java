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
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /kila history [玩家名] [页码] [-f]：查看对话历史（双维度，query.self / history.other）；-f 显示完整内容不缩略。
 * 数据源 kca_conversation，分页倒序。DB 查询在 IO 线程池执行，结果回主线程。
 */
public final class HistoryCommand {

    private static final int PAGE_SIZE = 8;

    private HistoryCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null) {
            sender.sendMessage(lm.getCommandHistoryNotInit());
            return;
        }

        // 先提取 -f（完整内容开关，可出现在任意参数位），剩余位置参数按原逻辑：数字→页码，否则玩家名
        boolean full = false;
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                full = true;
            } else {
                positional.add(args[i]);
            }
        }
        String nameArg = null;
        String pageArg = null;
        if (!positional.isEmpty()) {
            if (isNumeric(positional.get(0))) {
                pageArg = positional.get(0);
            } else {
                nameArg = positional.get(0);
                if (positional.size() >= 2) pageArg = positional.get(1);
            }
        }
        int page = parsePage(pageArg);

        QueryTarget.Resolved target = QueryTarget.resolve(sender, nameArg, PluginPermissionEnum.QUERY_SELF, PluginPermissionEnum.HISTORY_OTHER, false);
        if (target == null) return;

        final boolean fullContent = full; // full 在解析循环中被重新赋值，需 final 副本供 lambda 捕获
        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = dbManager.getConnection()) {
                UUID uuid = target.uuid();
                if (uuid == null) {
                    uuid = QueryTarget.resolveOfflineUuid(conn, dbManager.getTablePrefix(), target.displayName());
                    if (uuid == null) {
                        FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.replacePlaceholders(lm.getCommandHistoryPlayerNotFound(), "player", target.displayName())));
                        return;
                    }
                }
                ConversationDao dao = new ConversationDao(dbManager.getTablePrefix());
                int total = dao.countByPlayer(conn, uuid.toString());
                int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
                int currentPage = Math.max(1, Math.min(page, totalPages));
                int offset = (currentPage - 1) * PAGE_SIZE;
                List<ConversationDao.HistoryEntry> entries = dao.queryHistoryPage(conn, uuid.toString(), offset, PAGE_SIZE);
                Collections.reverse(entries); // DAO 倒序（最新在前）→ 反转为正序，页内从上往下按时间阅读
                List<String> lines = buildLines(target.displayName(), currentPage, totalPages, entries, lm, fullContent);
                FoliaCompat.runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (Exception e) {
                PluginLoggerUtil.error("历史", I18nService.tr("查询对话历史失败: {}", e.getMessage()), e);
                FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandHistoryQueryFailed()));
            }
        });
    }

    public static List<String> buildLines(String displayName, int page, int totalPages, List<ConversationDao.HistoryEntry> entries, LanguageManager lm, boolean full) {
        List<String> lines = new ArrayList<>();
        if (entries.isEmpty()) {
            lines.add(lm.replacePlaceholders(lm.getCommandHistoryEmpty(), "player", displayName));
            return lines;
        }
        lines.add(lm.replacePlaceholders(lm.getCommandHistoryTitle(), "player", displayName, "page", String.valueOf(page), "total", String.valueOf(totalPages)));
        for (ConversationDao.HistoryEntry e : entries) {
            String role = "user".equals(e.role()) ? lm.getCommandHistoryRoleUser() : "§bAI";
            String content = full ? e.content() : truncate(e.content(), 50);
            String time = AdminSkillUtil.formatTimestamp(e.createdAt());
            lines.add(lm.replacePlaceholders(lm.getCommandHistoryEntryLine(), "time", time, "role", role, "content", content));
        }
        if (totalPages > 1) {
            lines.add(lm.replacePlaceholders(lm.getCommandHistoryPagination(), "total", String.valueOf(totalPages)));
        }
        return lines;
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int parsePage(String arg) {
        return SkillsCommand.parsePage(arg);
    }

    public static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
