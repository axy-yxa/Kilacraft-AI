package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.cache.CacheMetricsCollector;
import com.zm.kilacraftAI.llm.cache.CacheStatsSnapshot;
import com.zm.kilacraftAI.llm.cache.TypeSnapshot;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /kila cache 命令 — 游戏内命中率条形图 + 控制台完整明细。
 * <p>
 * 游戏内采用两行式布局：类型名+命中率一行，条形图独占一行（固定缩进天然对齐，
 * 规避 CJK 与 ASCII 混排的像素级对齐问题）；控制台输出自适应分隔符的自洽表格，
 * 命中率以绿色标注。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheCommand {

    private static final int BAR_SEGMENTS = 30;

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

        FoliaCompat.getIOPool().execute(() -> {
            CacheStatsSnapshot snapshot = CacheMetricsCollector.getInstance().getSnapshot();
            List<String> gameLines = buildGameLines(snapshot, lm);
            FoliaCompat.runTask(plugin, () -> gameLines.forEach(sender::sendMessage));
            dumpConsole(snapshot, lm);
        });
    }

    /**
     * 游戏内展示：平均命中率收进标题行，明细为「类型名+命中率」与条形图两行一组。
     */
    public static List<String> buildGameLines(CacheStatsSnapshot snapshot, LanguageManager lm) {
        List<String> lines = new ArrayList<>();
        String mainModel = findMainModel(snapshot, lm);
        String header = lm.replacePlaceholders(lm.getCommandCacheHeader(), "model", mainModel);
        if (snapshot.totalRequests > 0) {
            header += " §8| §a" + lm.getCommandCacheAvgHit() + " " + pct(snapshot.getGlobalHitRate());
        }
        lines.add(header);

        if (snapshot.totalRequests == 0) {
            lines.add(lm.getCommandCacheNoData());
            lines.add("§7" + lm.getCommandCacheFooter());
            return lines;
        }

        lines.add("");
        for (TypeSnapshot type : snapshot.types) {
            if (type.requests == 0) continue;
            String name = lm.getCommandCacheTypeName(type.type);
            if (type.supported) {
                lines.add("§f▌" + name + " §a" + pct(type.getHitRate()));
                lines.add(" " + hitBarGame(type.getHitRate()));
            } else {
                lines.add("§f▌" + name + " §8N/A");
            }
        }

        lines.add("");
        lines.add("§7" + lm.getCommandCacheFooter());
        return lines;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0xF900 && c <= 0xFAFF) || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }

    private static String hitBarGame(double rate) {
        int filled = (int) Math.round(rate * BAR_SEGMENTS);
        if (filled < 0) filled = 0;
        if (filled > BAR_SEGMENTS) filled = BAR_SEGMENTS;
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            sb.append(i < filled ? "§a|" : "§7|");
        }
        sb.append("§8]");
        return sb.toString();
    }

    private static void dumpConsole(CacheStatsSnapshot snapshot, LanguageManager lm) {
        if (snapshot.totalRequests == 0) {
            PluginLoggerUtil.info("大模型缓存", "暂无数据");
            return;
        }
        String main = findMainModel(snapshot, lm);
        String diag = findDiagModel(snapshot, main);

        // 收集类型行数据
        List<String> typeNames = new ArrayList<>();
        List<String> typeRates = new ArrayList<>();
        List<String> typeHitInputs = new ArrayList<>();
        List<Boolean> typeSupported = new ArrayList<>();
        List<String> typeReqStrs = new ArrayList<>();
        int maxNameW = 0, maxRateW = 0, maxLeftW = 0, maxRightW = 0, maxReqW = 0;
        List<String> leftParts = new ArrayList<>();
        List<String> rightParts = new ArrayList<>();

        for (TypeSnapshot type : snapshot.types) {
            if (type.requests == 0) continue;
            String name = lm.getCommandCacheTypeName(type.type);
            typeNames.add(name);
            int nw = consoleWidth(name);
            if (nw > maxNameW) maxNameW = nw;

            typeSupported.add(type.supported);
            String reqStr = String.valueOf(type.requests);
            typeReqStrs.add(reqStr);
            if (reqStr.length() > maxReqW) maxReqW = reqStr.length();
            if (type.supported) {
                String r = pct(type.getHitRate());
                typeRates.add(r);
                if (r.length() > maxRateW) maxRateW = r.length();
                String l = f(type.cacheReadTokens);
                String rv = f(type.inputTokens);
                leftParts.add(l);
                rightParts.add(rv);
                if (l.length() > maxLeftW) maxLeftW = l.length();
                if (rv.length() > maxRightW) maxRightW = rv.length();
            } else {
                typeRates.add("N/A");
                if (3 > maxRateW) maxRateW = 3;
                String v = f(type.inputTokens);
                leftParts.add(v);
                rightParts.add(null);
                if (v.length() > maxLeftW) maxLeftW = v.length();
            }
        }

        for (int i = 0; i < leftParts.size(); i++) {
            if (rightParts.get(i) != null) {
                typeHitInputs.add(padAscii(leftParts.get(i), maxLeftW) + "/" + rightParts.get(i));
            } else {
                typeHitInputs.add(leftParts.get(i));
            }
        }

        // 内容行测宽，确定分隔符长度（行结构与下方 i18n 模板输出逐字一致）
        int maxW = 0;

        StringBuilder modelLine = new StringBuilder("主模型: ").append(main);
        if (!diag.isEmpty()) modelLine.append("  |  推理模型: ").append(diag);
        maxW = Math.max(maxW, consoleWidth(modelLine.toString()));

        String sumLine = "合计: 命中率 " + pct(snapshot.getGlobalHitRate()) + "  请求 " + snapshot.totalRequests + " 次" + "  命中 " + f(snapshot.totalCacheReadTokens) + "  输入 " + f(snapshot.totalInputTokens) + "  输出 " + f(snapshot.totalOutputTokens);
        maxW = Math.max(maxW, consoleWidth(sumLine));

        for (int i = 0; i < typeNames.size(); i++) {
            String name = padConsoleCjk(typeNames.get(i), maxNameW);
            String rate = padAscii(typeRates.get(i), maxRateW);
            String req = padAscii(typeReqStrs.get(i), maxReqW);
            String line;
            if (typeSupported.get(i)) {
                line = name + "  命中率 " + rate + "  请求 " + req + " 次" + "  命中/输入 " + typeHitInputs.get(i);
            } else {
                line = name + "  未报告缓存  请求 " + req + " 次  输入 " + typeHitInputs.get(i);
            }
            maxW = Math.max(maxW, consoleWidth(line));
        }

        // 算法说明输出在末尾分隔线下方，不参与测宽
        String algo1 = "算法: 平均命中率 = SUM(缓存命中token) / SUM(输入token)";
        String algo2 = "      单类命中率 = 该类型缓存命中token / 该类型输入token";

        String titleInner = I18nService.tr(" Kilacraft-AI 大模型缓存 ");
        PluginLoggerUtil.info("大模型缓存", bordered(titleInner, maxW));
        if (!diag.isEmpty()) {
            PluginLoggerUtil.info("大模型缓存", "主模型: {}  |  推理模型: {}", main, diag);
        } else {
            PluginLoggerUtil.info("大模型缓存", "主模型: {}", main);
        }
        PluginLoggerUtil.info("大模型缓存", repeat('-', maxW));
        PluginLoggerUtil.info("大模型缓存", "合计: 命中率 {}  请求 {} 次  命中 {}  输入 {}  输出 {}", pct(snapshot.getGlobalHitRate()), snapshot.totalRequests, f(snapshot.totalCacheReadTokens), f(snapshot.totalInputTokens), f(snapshot.totalOutputTokens));
        for (int i = 0; i < typeNames.size(); i++) {
            String name = padConsoleCjk(typeNames.get(i), maxNameW);
            String rate = padAscii(typeRates.get(i), maxRateW);
            String req = padAscii(typeReqStrs.get(i), maxReqW);
            if (typeSupported.get(i)) {
                PluginLoggerUtil.info("大模型缓存", "  {}  命中率 {}  请求 {} 次  命中/输入 {}/{}", name, rate, req, leftParts.get(i), rightParts.get(i));
            } else {
                PluginLoggerUtil.info("大模型缓存", "  {}  未报告缓存  请求 {} 次  输入 {}", name, req, typeHitInputs.get(i));
            }
        }
        PluginLoggerUtil.info("大模型缓存", repeat('-', maxW));
        PluginLoggerUtil.info("大模型缓存", algo1);
        PluginLoggerUtil.info("大模型缓存", algo2);
    }

    private static String bordered(String inner, int totalWidth) {
        int innerW = consoleWidth(inner);
        int left = (totalWidth - innerW) / 2;
        int right = totalWidth - innerW - left;
        return repeat('=', left) + inner + repeat('=', right);
    }

    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static int consoleWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += isCjk(c) ? 2 : 1;
        }
        return w;
    }

    private static String padConsoleCjk(String s, int targetWidth) {
        int cur = consoleWidth(s);
        StringBuilder sb = new StringBuilder(s);
        while (cur < targetWidth) {
            sb.append(' ');
            cur++;
        }
        return sb.toString();
    }

    private static String padAscii(String s, int targetWidth) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < targetWidth) sb.append(' ');
        return sb.toString();
    }

    private static String findMainModel(CacheStatsSnapshot snapshot, LanguageManager lm) {
        for (TypeSnapshot t : snapshot.types)
            if (t.requests > 0 && t.modelName != null && !t.modelName.isEmpty()) {
                int s = t.modelName.lastIndexOf('/');
                return s >= 0 ? t.modelName.substring(s + 1) : t.modelName;
            }
        return lm.getCommandCacheUnknownModel();
    }

    private static String findDiagModel(CacheStatsSnapshot snapshot, String main) {
        for (TypeSnapshot t : snapshot.types) {
            if (t.requests == 0 || t.modelName == null) continue;
            int s = t.modelName.lastIndexOf('/');
            String m = s >= 0 ? t.modelName.substring(s + 1) : t.modelName;
            if (!m.equals(main)) return m;
        }
        return "";
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private static String f(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fK", n / 1000.0);
        return String.format("%.1fM", n / 1_000_000.0);
    }
}
