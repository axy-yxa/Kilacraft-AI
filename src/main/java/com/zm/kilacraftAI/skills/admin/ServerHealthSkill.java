package com.zm.kilacraftAI.skills.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.AdminConfigManager;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 服务器健康管理技能
 *
 * <p>提供历史告警查询、诊断报告列表浏览和报告内容读取能力。</p>
 * <ul>
 *   <li>{@code health_report} — 查询历史告警记录（数据源: kca_server_event 表）</li>
 *   <li>{@code list_reports} — 列出报告目录中的诊断报告文件</li>
 *   <li>{@code read_report} — 读取指定诊断报告的完整内容（自动剥离推理过程折叠块）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class ServerHealthSkill implements Skill {

    private static final String SKILL_NAME = "server_health";
    private static final String LOG_PREFIX = "健康监控";

    /**
     * 报告文件名前缀
     */
    private static final String REPORT_PREFIX = "health_report_";

    /**
     * 系统热点名称集合 — 这些不是已安装插件，在聚合统计中应与插件分离，避免误导 LLM。
     * Minecraft = 服务端内核（net.minecraft.* / ca.spottedleaf.*），
     * 其他常见基础包名也可能作为系统热点出现。
     */
    private static final Set<String> SYSTEM_HOTSPOT_NAMES = Set.of("Minecraft", "Kilacraft-AI (自身)");
    /**
     * 报告文件名中的日期格式
     */
    private static final String FILE_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";
    /**
     * 报告文件名日期解析器
     */
    private static final DateTimeFormatter PARSE_FMT = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
    /**
     * 显示用日期格式
     */
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 推理过程折叠块正则（中文）
     */
    private static final String REASONING_PATTERN_ZH = "(?s)<details><summary>AI 推理过程</summary>.*?</details>\\s*";
    /**
     * 推理过程折叠块正则（英文）
     */
    private static final String REASONING_PATTERN_EN = "(?s)<details><summary>AI Reasoning Process</summary>.*?</details>\\s*";

    private final SkillConfigManager configManager;

    public ServerHealthSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return (config != null && !config.getDescription().isEmpty()) ? config.getDescription() : null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null && !config.getHints().isEmpty()) ? new ArrayList<>(config.getHints()) : Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.ADMIN_HEALTH.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // 权限校验：player 为 null 视为无权限（正常调用路径必有在线 player）
        Player caller = context.getPlayer();
        if (caller == null || !PluginPermissionEnum.ADMIN_HEALTH.hasPermission(caller)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.ADMIN_HEALTH.getNode())));
        }

        String action = context.getAction();
        if (action == null || action.isEmpty()) {
            action = "health_report";
        }

        return switch (action) {
            case "health_report" -> executeQueryMode(context);
            case "list_reports" -> executeListReports(context);
            case "read_report" -> executeReadReport(context);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action)).toFuture();
        };
    }

    /**
     * 历史告警查询（query 模式）
     */
    private CompletableFuture<SkillResult> executeQueryMode(SkillContext context) {
        // 解析查询参数
        String pluginFilter = SkillEntityHelper.getString(context, "plugin");
        String metricTypeFilter = SkillEntityHelper.getString(context, "metric_type");
        String timeRange = SkillEntityHelper.getString(context, "time_range", "7d");

        long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
        if (afterTime < 0) {
            return SkillResult.failure(I18nService.tr("无效的时间范围: {}（支持: 1d/3d/7d/30d）", timeRange)).toFuture();
        }

        DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
        if (dbManager == null) {
            return SkillResult.failure(I18nService.tr("数据库未初始化")).toFuture();
        }

        final String plugin = pluginFilter;
        final String metricType = metricTypeFilter;
        final long queryAfterTime = afterTime;

        // 异步执行 DB 查询 + Java 层聚合
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                ServerEventDao dao = new ServerEventDao(dbManager.getTablePrefix());
                List<ServerEvent> alerts = dao.loadHealthAlerts(conn, queryAfterTime, 200);

                if (alerts.isEmpty()) {
                    return SkillResult.success(I18nService.tr("过去 {} 内没有健康告警记录。", timeRange));
                }

                return buildQueryResult(alerts, plugin, metricType, timeRange);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询历史告警失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询历史告警失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 解析告警 data JSON，构建查询结果。
     * 聚合维度：告警次数、涉及插件列表、时间分布、最低 TPS 告警详情。
     */
    private SkillResult buildQueryResult(List<ServerEvent> alerts, String pluginFilter, String metricTypeFilter, String timeRange) {
        List<JsonObject> parsedAlerts = new ArrayList<>();
        Map<String, Integer> pluginAlertCounts = new LinkedHashMap<>();
        Map<String, Integer> systemAlertCounts = new LinkedHashMap<>();
        Map<String, Integer> dailyDistribution = new LinkedHashMap<>();
        JsonObject lowestTpsAlert = null;
        double lowestTps = Double.MAX_VALUE;

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("MM-dd");

        for (ServerEvent event : alerts) {
            String dataStr = event.getData();
            if (dataStr == null || dataStr.isEmpty()) continue;

            JsonObject data;
            try {
                data = JsonParser.parseString(dataStr).getAsJsonObject();
            } catch (Exception e) {
                continue;
            }

            // metric_type 过滤
            if (metricTypeFilter != null && !metricTypeFilter.isEmpty()) {
                String metricLower = metricTypeFilter.toLowerCase();
                boolean matches = true;
                if (data.has("alerts")) {
                    matches = false;
                    for (var elem : data.getAsJsonArray("alerts")) {
                        var alertObj = elem.getAsJsonObject();
                        String metric = alertObj.has("metric") ? alertObj.get("metric").getAsString() : "";
                        if ((metricLower.equals("tps") && metric.contains("tps")) || (metricLower.equals("mspt") && metric.contains("mspt")) || (metricLower.equals("cpu") && metric.contains("cpu"))) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (!matches) continue;
            }

            // plugin 过滤
            if (pluginFilter != null && !pluginFilter.isEmpty()) {
                JsonArray hotspots = data.has("top_hotspots") ? data.getAsJsonArray("top_hotspots") : null;
                if (hotspots == null || !containsPlugin(hotspots, pluginFilter)) {
                    continue;
                }
            }

            // 统计涉及插件（区分已安装插件 vs 系统内核）
            JsonArray hotspots = data.has("top_hotspots") ? data.getAsJsonArray("top_hotspots") : null;
            if (hotspots != null) {
                for (JsonElement elem : hotspots) {
                    JsonObject hs = elem.getAsJsonObject();
                    String pluginName = hs.has("plugin") ? hs.get("plugin").getAsString() : "unknown";
                    if (SYSTEM_HOTSPOT_NAMES.contains(pluginName)) {
                        systemAlertCounts.merge(pluginName, 1, Integer::sum);
                    } else {
                        pluginAlertCounts.merge(pluginName, 1, Integer::sum);
                    }
                }
            }

            // 时间分布统计
            String day = Instant.ofEpochMilli(event.getCreatedAt()).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt);
            dailyDistribution.merge(day, 1, Integer::sum);

            // 记录 TPS 最低的告警
            if (data.has("tps_1m")) {
                double tps = data.get("tps_1m").getAsDouble();
                if (tps < lowestTps) {
                    lowestTps = tps;
                    lowestTpsAlert = data;
                }
            }

            parsedAlerts.add(data);
        }

        if (parsedAlerts.isEmpty()) {
            String msg = I18nService.tr("过去 {} 内没有匹配的告警记录", timeRange);
            if (pluginFilter != null) msg += I18nService.tr("（插件: {}）", pluginFilter);
            if (metricTypeFilter != null) msg += I18nService.tr("（指标: {}）", metricTypeFilter);
            return SkillResult.success(msg);
        }

        // 构建返回数据
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("alert_count", parsedAlerts.size());
        resultData.put("plugins", pluginAlertCounts);
        resultData.put("system", systemAlertCounts);
        resultData.put("daily_distribution", dailyDistribution);
        resultData.put("time_range", timeRange);

        // TPS 最低的告警详情
        if (lowestTpsAlert != null) {
            Map<String, Object> lowestTpsInfo = new LinkedHashMap<>();
            lowestTpsInfo.put("tps_1m", lowestTpsAlert.has("tps_1m") ? lowestTpsAlert.get("tps_1m").getAsDouble() : null);
            lowestTpsInfo.put("mspt_p95", lowestTpsAlert.has("mspt_p95") ? lowestTpsAlert.get("mspt_p95").getAsDouble() : null);
            lowestTpsInfo.put("cpu_process", lowestTpsAlert.has("cpu_process") ? lowestTpsAlert.get("cpu_process").getAsDouble() : null);
            lowestTpsInfo.put("alerts", lowestTpsAlert.has("alerts") ? lowestTpsAlert.get("alerts").toString() : "");
            if (lowestTpsAlert.has("top_hotspots")) {
                lowestTpsInfo.put("top_hotspots", lowestTpsAlert.get("top_hotspots").toString());
            }
            resultData.put("lowest_tps_detail", lowestTpsInfo);
        }

        // 构建可读摘要
        String summary = formatQuerySummary(parsedAlerts.size(), pluginAlertCounts, systemAlertCounts, dailyDistribution, lowestTps, pluginFilter, metricTypeFilter, timeRange);
        return SkillResult.success(summary, resultData);
    }

    /**
     * 检查 top_hotspots 中是否包含指定插件名
     */
    private boolean containsPlugin(JsonArray hotspots, String pluginName) {
        String target = pluginName.toLowerCase();
        for (JsonElement elem : hotspots) {
            JsonObject hs = elem.getAsJsonObject();
            if (hs.has("plugin") && hs.get("plugin").getAsString().toLowerCase().contains(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 格式化查询结果摘要
     */
    private String formatQuerySummary(int alertCount, Map<String, Integer> pluginCounts, Map<String, Integer> systemCounts, Map<String, Integer> dailyDist, double lowestTps, String pluginFilter, String metricFilter, String timeRange) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("过去 {} 共 {} 次健康告警", timeRange, alertCount));

        if (pluginFilter != null) {
            sb.append(I18nService.tr("（过滤插件: {}）", pluginFilter));
        }
        if (metricFilter != null) {
            sb.append(I18nService.tr("（过滤指标: {}）", metricFilter));
        }

        if (!pluginCounts.isEmpty()) {
            sb.append("\n").append(I18nService.tr("涉及插件: "));
            pluginCounts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5).forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append(I18nService.tr("次) ")));
        }

        if (!systemCounts.isEmpty()) {
            sb.append("\n").append(I18nService.tr("系统热点: "));
            systemCounts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append(I18nService.tr("次) ")));
            sb.append(I18nService.tr("（系统热点为服务端内核/自身插件的基础开销，通常无需关注）"));
        }

        if (!dailyDist.isEmpty()) {
            sb.append("\n").append(I18nService.tr("时间分布: "));
            dailyDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append(I18nService.tr("次) ")));
        }

        if (lowestTps < Double.MAX_VALUE) {
            sb.append("\n").append(I18nService.tr("最低 TPS: {}", String.format("%.1f", lowestTps)));
        }

        return sb.toString();
    }

    /**
     * 列出报告目录中的诊断报告文件
     */
    private CompletableFuture<SkillResult> executeListReports(SkillContext context) {
        String timeRangeStr = SkillEntityHelper.getString(context, "time_range");
        String modeFilter = SkillEntityHelper.getString(context, "mode");
        String limitStr = SkillEntityHelper.getString(context, "limit");

        final String timeRange = (timeRangeStr != null) ? timeRangeStr : "7d";
        final int limit = AdminSkillUtil.parseLimit(limitStr, 20);

        long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
        if (afterTime < 0) {
            return SkillResult.failure(I18nService.tr("无效的时间范围: {}（支持: 1d/3d/7d/30d）", timeRange)).toFuture();
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                File reportDir = getReportDirectory();
                if (reportDir == null) {
                    return SkillResult.success(I18nService.tr("报告目录不存在或为空"), Map.of("count", 0, "reports", List.of()));
                }

                List<Map<String, Object>> reportList = performListReports(reportDir, modeFilter, afterTime, limit);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("mode", (modeFilter != null && !modeFilter.isEmpty()) ? modeFilter : null);
                data.put("count", reportList.size());
                data.put("reports", reportList);

                if (reportList.isEmpty()) {
                    return SkillResult.success(I18nService.tr("未找到报告文件（{}）", timeRange), data);
                }

                // 构建包含报告列表详情的摘要（确保 LLM 二次分析能看到具体报告）
                StringBuilder summary = new StringBuilder();
                summary.append(I18nService.tr("找到 {} 份报告（{}）", reportList.size(), timeRange));
                for (Map<String, Object> r : reportList) {
                    summary.append("\n  - ").append(r.get("filename"));
                    summary.append(" (").append(r.get("mode"));
                    summary.append(", ").append(r.get("timestamp"));
                    summary.append(", ").append(r.get("size_display")).append(")");
                }
                return SkillResult.success(summary.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("列出报告失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("列出报告失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 读取指定诊断报告的完整内容（自动剥离推理过程折叠块）
     */
    private CompletableFuture<SkillResult> executeReadReport(SkillContext context) {
        String filenameRaw = SkillEntityHelper.getString(context, "filename");
        String indexStr = SkillEntityHelper.getString(context, "index");
        String modeFilter = SkillEntityHelper.getString(context, "mode");

        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = filenameRaw;

                // index 模式：先查 list 找对应文件
                if ((filename == null || filename.isEmpty()) && indexStr != null && !indexStr.isEmpty()) {
                    int index;
                    try {
                        index = Integer.parseInt(indexStr);
                    } catch (NumberFormatException e) {
                        return SkillResult.failure(I18nService.tr("无效的索引值"));
                    }

                    File reportDir = getReportDirectory();
                    if (reportDir == null) {
                        return SkillResult.failure(I18nService.tr("报告目录不存在"));
                    }

                    // 应用 mode 过滤（如果上游步骤指定了 mode，如多步骤任务中的 list_reports）
                    List<Map<String, Object>> reports = performListReports(reportDir, modeFilter, -1, 100);
                    if (index < 1 || index > reports.size()) {
                        return SkillResult.failure(I18nService.tr("索引超出范围（共 {} 份报告）", reports.size()));
                    }
                    filename = (String) reports.get(index - 1).get("filename");
                }

                // 参数校验
                if (filename == null || filename.isEmpty()) {
                    return SkillResult.failure(I18nService.tr("请指定 filename 或 index 参数"));
                }

                // 安全检查
                SkillResult securityCheck = validateFilename(filename);
                if (securityCheck != null) {
                    return securityCheck;
                }

                File reportDir = getReportDirectory();
                if (reportDir == null) {
                    return SkillResult.failure(I18nService.tr("报告目录不存在"));
                }

                File reportFile = new File(reportDir, filename);

                // 路径穿越检查
                if (!reportFile.getCanonicalPath().startsWith(reportDir.getCanonicalPath())) {
                    return SkillResult.failure(I18nService.tr("无效的文件路径"));
                }
                if (!reportFile.exists() || !reportFile.isFile()) {
                    return SkillResult.failure(I18nService.tr("报告文件不存在: {}", filename));
                }

                // 读取内容
                String content = Files.readString(reportFile.toPath(), StandardCharsets.UTF_8);

                // 剥离 AI 推理过程的 <details> 折叠块（对 AI 再次分析无价值，节省约 30-40% 内容）
                content = content.replaceAll(REASONING_PATTERN_ZH, "");
                content = content.replaceAll(REASONING_PATTERN_EN, "");

                // 解析文件名元信息
                String[] parsed = parseReportFilename(filename);
                if (parsed == null) {
                    return SkillResult.failure(I18nService.tr("无法解析报告文件名: {}", filename));
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("filename", filename);
                data.put("mode", parsed[0]);
                data.put("timestamp", parsed[1]);
                data.put("size", reportFile.length());
                data.put("content", content);

                // message 包含报告全文，确保 LLM 二次分析能看到完整内容
                String summary = I18nService.tr("已读取报告 {}（{}）", filename, AdminSkillUtil.formatFileSize(reportFile.length()));
                return SkillResult.success(summary + "\n\n" + content, data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("读取报告失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("读取报告失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 获取报告输出目录
     *
     * @return 报告目录（已存在且为目录），不存在或无效时返回 null
     */
    private File getReportDirectory() {
        AdminConfigManager adminConfig = KilacraftAI.getInstance().getAdminConfigManager();
        if (adminConfig == null) return null;
        File reportDir = adminConfig.getReportOutputDirectory();
        if (reportDir == null || !reportDir.exists() || !reportDir.isDirectory()) return null;
        return reportDir;
    }

    /**
     * 校验文件名安全性（防路径穿越）
     *
     * @param filename 待校验的文件名
     * @return 非 null 表示校验失败（返回 failure 结果），null 表示通过
     */
    private SkillResult validateFilename(String filename) {
        // 字符白名单
        if (!filename.matches("^[a-zA-Z0-9_\\-\\.]+$")) {
            return SkillResult.failure(I18nService.tr("无效的文件名"));
        }
        // 扩展名校验
        if (!filename.endsWith(".md")) {
            return SkillResult.failure(I18nService.tr("仅支持读取 .md 文件"));
        }
        // 禁止路径分隔符
        if (filename.contains("/") || filename.contains("\\")) {
            return SkillResult.failure(I18nService.tr("无效的文件名"));
        }
        return null;
    }

    /**
     * 从报告文件名中解析 mode 和 timestamp。
     * 文件名格式: health_report_{mode}_{yyyy-MM-dd_HH-mm-ss}.md
     *
     * @param filename 文件名（不含路径）
     * @return [mode, timestamp_display_string, epoch_millis]，解析失败返回 null
     */
    private String[] parseReportFilename(String filename) {
        if (filename == null || !filename.startsWith(REPORT_PREFIX) || !filename.endsWith(".md")) return null;
        String core = filename.substring(REPORT_PREFIX.length(), filename.length() - ".md".length());
        int firstUnderscore = core.indexOf('_');
        if (firstUnderscore <= 0) return null;

        String mode = core.substring(0, firstUnderscore);
        if (!"auto".equals(mode) && !"manual".equals(mode)) return null;

        String tsStr = core.substring(firstUnderscore + 1);
        try {
            LocalDateTime ldt = LocalDateTime.parse(tsStr, PARSE_FMT);
            return new String[]{mode, ldt.format(DISPLAY_FMT), String.valueOf(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())};
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 执行报告列表查询（内部方法，供 list_reports 和 read_report index 模式复用）
     *
     * @param reportDir  报告目录
     * @param modeFilter auto/manual/null（不过滤）
     * @param afterTime  时间过滤起始（ms），-1 表示不过滤
     * @param limit      最大返回数
     * @return 报告列表（已按时间倒序排列、截断）
     */
    private List<Map<String, Object>> performListReports(File reportDir, String modeFilter, long afterTime, int limit) {
        File[] files = reportDir.listFiles((dir, name) -> name.startsWith(REPORT_PREFIX) && name.endsWith(".md"));

        if (files == null || files.length == 0) {
            return List.of();
        }

        List<Map<String, Object>> reportList = new ArrayList<>();

        for (File file : files) {
            String name = file.getName();
            String[] parsed = parseReportFilename(name);
            if (parsed == null) continue;

            String mode = parsed[0];

            // 按 mode 过滤
            if (modeFilter != null && !modeFilter.isEmpty() && !modeFilter.equalsIgnoreCase(mode)) continue;

            // 按时间过滤
            if (afterTime > 0) {
                long fileTimeMs = Long.parseLong(parsed[2]);
                if (fileTimeMs < afterTime) continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("filename", name);
            entry.put("mode", mode);
            entry.put("timestamp", parsed[1]);
            entry.put("size", file.length());
            entry.put("size_display", AdminSkillUtil.formatFileSize(file.length()));
            reportList.add(entry);
        }

        // 按时间倒序
        reportList.sort((a, b) -> ((String) b.get("timestamp")).compareTo((String) a.get("timestamp")));

        // 截断
        if (reportList.size() > limit) {
            reportList = reportList.subList(0, limit);
        }

        return reportList;
    }
}
