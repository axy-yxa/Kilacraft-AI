package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.llm.cache.CacheMetricsCollector;
import com.zm.kilacraftAI.llm.cache.CacheStatsSnapshot;
import com.zm.kilacraftAI.llm.cache.TypeSnapshot;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /kila cache 命令 — 显示大模型缓存命中统计。
 * <p>
 * Minecraft 聊天框不适合使用等宽表格；每页仅展示一个调用类型，
 * 避免长模型名和多类型统计互相挤压而导致关键数据不可读。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheCommand {

    private CacheCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();

        if (!PluginPermissionEnum.ADMIN_CACHE.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandCacheNoPermission());
            return;
        }

        if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
            CacheMetricsCollector.getInstance().reset();
            sender.sendMessage(lm.getCommandCacheResetSuccess());
            return;
        }

        int page = parsePage(args, lm, sender);
        if (page < 0) {
            return;
        }

        FoliaCompat.getIOPool().execute(() -> {
            CacheStatsSnapshot snapshot = CacheMetricsCollector.getInstance().getSnapshot();
            List<String> lines = buildLines(snapshot, page, lm);
            FoliaCompat.runTask(plugin, () -> lines.forEach(sender::sendMessage));
        });
    }

    private static int parsePage(String[] args, LanguageManager lm, CommandSender sender) {
        if (args.length < 2) {
            return 1;
        }
        try {
            int page = Integer.parseInt(args[1]);
            if (page > 0) {
                return page;
            }
        } catch (NumberFormatException ignored) {
        }
        sender.sendMessage(lm.getCommandCacheInvalidPage());
        return -1;
    }

    public static List<String> buildLines(CacheStatsSnapshot snapshot, int page, LanguageManager lm) {
        List<TypeSnapshot> types = snapshot.types.stream().filter(type -> type.requests > 0).toList();
        int totalPages = Math.max(1, types.size());
        if (page > totalPages) {
            return List.of(lm.replacePlaceholders(lm.getCommandCachePageOutOfRange(), "total", String.valueOf(totalPages)));
        }

        List<String> lines = new ArrayList<>();
        lines.add(lm.replacePlaceholders(lm.getCommandCacheHeader(), "page", String.valueOf(page), "total", String.valueOf(totalPages)));
        if (snapshot.totalRequests == 0) {
            lines.add(lm.getCommandCacheNoData());
            addFooterLines(lines, lm);
            return lines;
        }

        lines.add(lm.replacePlaceholders(lm.getCommandCacheGlobalLine(), "requests", String.valueOf(snapshot.totalRequests), "input", formatNumber(snapshot.totalInputTokens), "output", formatNumber(snapshot.totalOutputTokens), "total", formatNumber(snapshot.totalTokens)));
        lines.add(lm.replacePlaceholders(lm.getCommandCacheGlobalRateLine(), "hitrate", formatPercent(snapshot.getGlobalHitRate()), "saved", formatNumber(snapshot.totalCacheReadTokens)));

        TypeSnapshot type = types.get(page - 1);
        String model = type.modelName != null ? type.modelName : lm.getCommandCacheUnknownModel();
        lines.add(lm.replacePlaceholders(lm.getCommandCacheTypeHeader(), "type", lm.getCommandCacheTypeName(type.type), "model", model));
        lines.add(lm.replacePlaceholders(lm.getCommandCacheTypeUsageLine(), "requests", String.valueOf(type.requests), "input", formatNumber(type.inputTokens), "output", formatNumber(type.outputTokens), "total", formatNumber(type.totalTokens)));
        if (type.supported) {
            lines.add(lm.replacePlaceholders(lm.getCommandCacheTypeMetricsLine(), "hitrate", formatPercent(type.getHitRate()), "saved", formatNumber(type.getSavedTokens())));
        } else {
            lines.add(lm.getCommandCacheUnsupported());
        }

        addFooterLines(lines, lm);
        return lines;
    }

    private static void addFooterLines(List<String> lines, LanguageManager lm) {
        lines.add(lm.getCommandCacheFooterNote1());
        lines.add(lm.getCommandCacheFooterNote2());
        lines.add(lm.getCommandCacheFooterNote3());
    }

    private static String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private static String formatNumber(long n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        if (n < 1_000_000) {
            return String.format("%.1fK", n / 1000.0);
        }
        if (n < 1_000_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        }
        return String.format("%.1fG", n / 1_000_000_000.0);
    }
}
