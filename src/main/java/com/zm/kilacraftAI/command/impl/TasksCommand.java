package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.scheduler.TaskScheduler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /kila tasks：查看定时任务状态（tasks 权限）。
 * 数据来自 {@link TaskScheduler#getTaskStatuses()}，卡片式展示在本命令完成，文案走 language.yml。
 */
public final class TasksCommand {

    private TasksCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.TASKS.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandTasksNoPermission());
            return;
        }
        TaskScheduler scheduler = plugin.getTaskScheduler();
        if (scheduler == null) {
            sender.sendMessage(lm.getCommandTasksNotInit());
            return;
        }
        List<String> lines = buildLines(scheduler.getTaskStatuses(), lm);
        lines.forEach(sender::sendMessage);
    }

    /**
     * 构建卡片式任务状态展示行：每个任务独立区块（标题行 + 缩进统计/上次/异常行）。
     * 放弃表格对齐（Minecraft 字体不等宽，边框无法对齐），文案全部走 language.yml。
     */
    public static List<String> buildLines(List<TaskScheduler.TaskStatus> statuses, LanguageManager lm) {
        List<String> lines = new ArrayList<>();
        lines.add(lm.replacePlaceholders(lm.getCommandTasksTitle(), "n", String.valueOf(statuses.size())));
        if (statuses.isEmpty()) {
            lines.add(lm.getCommandTasksEmpty());
            return lines;
        }
        for (TaskScheduler.TaskStatus s : statuses) {
            String interval = formatInterval(s.intervalTicks());
            // 统计：有累计 → stats-count；已执行但无累计 → no-trigger；从未执行 → -
            String stats;
            if (s.totalProcessed() > 0) {
                stats = lm.replacePlaceholders(lm.getCommandTasksStatsCount(), "n", String.valueOf(s.totalProcessed()));
            } else if (s.lastExecuteTime() > 0) {
                stats = lm.getCommandTasksNoTrigger();
            } else {
                stats = "§8-";
            }
            String lastRun = s.lastExecuteTime() > 0 ? lm.replacePlaceholders(lm.getCommandTasksTimeAgo(), "n", formatElapsed(System.currentTimeMillis() - s.lastExecuteTime())) : lm.getCommandTasksNeverRun();
            // 状态符号：颜色直接反映状态（正常●绿 / 等待首次○灰 / 异常✗红）
            String mark;
            if (s.lastError() != null) {
                mark = "§c✗";
            } else if (s.lastExecuteTime() == 0) {
                mark = "§7○";
            } else {
                mark = "§a●";
            }
            lines.add("");
            lines.add(lm.replacePlaceholders(lm.getCommandTaskHeader(), "mark", mark, "name", s.name(), "interval", interval));
            lines.add(lm.replacePlaceholders(lm.getCommandTasksStatsLine(), "stats", stats));
            lines.add(lm.replacePlaceholders(lm.getCommandTasksLastRunLine(), "time", lastRun));
            if (s.lastError() != null) {
                lines.add(lm.replacePlaceholders(lm.getCommandTasksErrorLine(), "error", truncate(s.lastError(), 40)));
            }
        }
        return lines;
    }

    /**
     * 将 ticks 转换为人类可读的时间间隔（如 30s/5min/1h/2d）
     */
    private static String formatInterval(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "min";
        long hours = seconds / 3600;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    /**
     * 将毫秒转换为人类可读的经过时间（如 5s/12min/1h/2d）
     */
    private static String formatElapsed(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "min";
        long hours = seconds / 3600;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }
}
