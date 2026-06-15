package com.zm.kilacraftAI.service.health;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.JsonSafeGetUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.AdminConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 诊断报告生成器
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class DiagnosticReportGenerator {

    private static final String LOG_PREFIX = "健康监控";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminConfigManager configManager;

    public DiagnosticReportGenerator(AdminConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 生成诊断报告文件
     *
     * @param snapshot        当前健康快照
     * @param profilerUrl     Spark Profiler viewer URL（可能为 null）
     * @param metadataJson    Profiler 元数据 JSON（可能为 null）
     * @param aiDiagnosis     AI 推理模型诊断结论
     * @param processedResult Protobuf 解析后的热点数据（可能为 null）
     * @param mode            触发模式（auto / manual）
     * @param alerts          告警列表（auto 模式传入 {@code checkThresholds()} 返回值，manual 模式传入空列表）
     * @param serverPlatform  服务端平台信息（由调用线程预加载，避免异步线程调用 Bukkit API）
     * @param activityBefore  Profiler 采样前活动快照
     * @param activityAfter   Profiler 采样后活动快照
     * @return 报告文件，失败返回 null
     */
    public File generateReport(SparkDataCollector.HealthSnapshot snapshot, String profilerUrl, String metadataJson, String aiDiagnosis, StackTraceProcessor.ProcessedResult processedResult, String mode, List<String> alerts, String serverPlatform, ServerActivitySnapshot activityBefore, ServerActivitySnapshot activityAfter) {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String modePrefix = mode != null ? mode : "unknown";
        String filename = "health_report_" + modePrefix + "_" + timestamp + ".md";

        File outputDir = configManager.getReportOutputDirectory();
        File reportFile = new File(outputDir, filename);

        StringBuilder content = new StringBuilder();
        String reportTime = LocalDateTime.now().format(REPORT_DATE_FORMAT);

        // 报告头
        content.append(I18nService.tr("# 服务器健康诊断报告")).append("\n\n");
        content.append("| ").append(I18nService.tr("项目")).append(" | ").append(I18nService.tr("值")).append(" |\n|------|----|\n");
        content.append("| ").append(I18nService.tr("触发模式")).append(" | ").append(mode != null ? mode : "unknown").append(" |\n");
        // 服务端平台信息：优先使用预加载值（Folia 兼容），降级使用 Bukkit API
        String platform = serverPlatform != null ? serverPlatform : AdminSkillUtil.getServerPlatform();
        content.append("| ").append(I18nService.tr("服务端")).append(" | ").append(platform).append(" |\n");
        content.append("| ").append(I18nService.tr("生成时间")).append(" | ").append(reportTime).append(" |\n");
        // 诊断模型：显示实际生成 AI 诊断结论的模型名（admin.yml 显式 或 llm.yml 回退，由 configManager 解析）
        content.append("| ").append(I18nService.tr("诊断模型")).append(" | ").append(configManager.getThinkingModelConfig().model()).append(" |\n");
        if (profilerUrl != null) {
            content.append("| Spark Viewer | [").append(profilerUrl).append("](").append(profilerUrl).append(") |\n");
            content.append("| ").append(I18nService.tr("原始数据")).append(" | [").append(profilerUrl).append("?raw=1](").append(profilerUrl).append("?raw=1) |\n");
        }
        content.append("\n---\n\n");

        content.append(I18nService.tr("## 1. 服务器状态概览")).append("\n\n");

        // 触发原因（仅 auto 模式且有告警时显示）
        if ("auto".equals(mode) && alerts != null && !alerts.isEmpty()) {
            content.append(I18nService.tr("### 1.1 触发原因")).append("\n\n");
            content.append(I18nService.tr("> 以下为守护线程检测到异常时的**实时快照值**，与下方 §1.2 采样期间的统计值可能不同。")).append("\n");
            content.append(I18nService.tr("> 例如：异常触发时 TPS=3.2，但经过 30s 采样后 TPS 可能已恢复到 20.0。")).append("\n\n");
            content.append("| ").append(I18nService.tr("指标")).append(" | ").append(I18nService.tr("触发值")).append(" | ").append(I18nService.tr("阈值")).append(" |\n|------|--------|------|\n");
            Map<String, Double> thresholds = configManager.getAlertThresholds();
            for (String alert : alerts) {
                String metricLabel;
                String valueStr;
                String thresholdStr;
                if (alert.contains("TPS")) {
                    metricLabel = "TPS 1m";
                    valueStr = snapshot != null && snapshot.tps1m() != null ? String.format("%.1f", snapshot.tps1m()) : "-";
                    thresholdStr = "< " + thresholds.getOrDefault("tps_threshold", 15.0).intValue();
                } else if (alert.contains("MSPT max")) {
                    metricLabel = I18nService.tr("MSPT 最大值");
                    double msptMaxValue = snapshot != null && snapshot.mspt10sMax() > 0 ? snapshot.mspt10sMax() : (snapshot != null ? snapshot.msptMax() : -1);
                    valueStr = msptMaxValue > 0 ? String.format("%.1fms", msptMaxValue) : "-";
                    thresholdStr = "> " + thresholds.getOrDefault("mspt_max_threshold", 50.0).intValue() + "ms";
                } else if (alert.contains("MSPT")) {
                    metricLabel = "MSPT P95";
                    valueStr = snapshot != null ? String.format("%.1fms", snapshot.msptP95()) : "-";
                    thresholdStr = "> " + thresholds.getOrDefault("mspt_p95_threshold", 50.0).intValue() + "ms";
                } else if (alert.contains("CPU")) {
                    metricLabel = "CPU";
                    valueStr = snapshot != null ? String.format("%.1f%%", snapshot.cpuProcess()) : "-";
                    thresholdStr = "> " + thresholds.getOrDefault("cpu_threshold", 80.0).intValue() + "%";
                } else {
                    metricLabel = alert;
                    valueStr = "-";
                    thresholdStr = "-";
                }
                content.append("| ").append(metricLabel).append(" | ").append(valueStr).append(" | ").append(thresholdStr).append(" |\n");
            }
            content.append("\n");
        }

        // 性能指标（采样期间） + 服务器概况
        if (metadataJson != null) {
            content.append(extractMetadataSummary(metadataJson));
        } else {
            content.append(I18nService.tr("无元数据")).append("\n");
        }

        // 服务器活动指标（采样窗口内的区块加载变化和玩家活动）
        appendActivityMetrics(content, activityBefore, activityAfter);

        // GC 信息（来自实时快照）
        if (snapshot != null && !snapshot.gcInfo().isEmpty()) {
            content.append("| ").append(I18nService.tr("GC 收集器")).append(" | ").append(I18nService.tr("次数")).append(" | ").append(I18nService.tr("总耗时")).append(" | ").append(I18nService.tr("均耗时")).append(" | ").append(I18nService.tr("频率")).append(" |\n");
            content.append("|-----------|------|--------|--------|------|\n");
            snapshot.gcInfo().forEach((name, info) -> {
                content.append("| ").append(name).append(" | ").append(info.totalCollections()).append(" | ").append(info.totalTime()).append("ms").append(" | ").append(String.format("%.1f", info.avgTime())).append("ms").append(" | ").append(info.avgFrequency()).append("s").append(" |\n");
            });
            content.append("\n");
        }

        // 自监控（折叠展示）
        if (snapshot != null) {
            content.append("<details><summary>Kilacraft-AI ").append(I18nService.tr("自监控")).append("</summary>\n\n");
            content.append("```\n");
            appendSelfMonitoring(content);
            content.append("```\n\n");
            content.append("</details>\n\n");
        }

        // 插件性能分析
        content.append(I18nService.tr("## 2. 插件性能分析")).append("\n\n");
        if (processedResult != null && !processedResult.hotspots().isEmpty()) {
            content.append(I18nService.tr("Server thread 采样占比: {}", String.format("%.2f%%", processedResult.serverThreadRatio()))).append("\n\n");

            // 已安装插件热点
            Map<String, Double> pluginHotspots = processedResult.pluginHotspots();
            if (!pluginHotspots.isEmpty()) {
                content.append(I18nService.tr("### 已安装插件耗时")).append("\n\n");
                content.append("| ").append(I18nService.tr("插件")).append(" | ").append(I18nService.tr("采样占比")).append(" |\n|------|----------|\n");
                for (var entry : pluginHotspots.entrySet()) {
                    content.append("| ").append(entry.getKey()).append(" | ").append(String.format("%.2f%%", entry.getValue())).append(" |\n");
                }
                content.append("\n");
            } else {
                content.append(I18nService.tr("采样期间未检测到已安装插件的热点活动。")).append("\n\n");
            }

            // 系统/核心耗时（折叠展示）
            Map<String, Double> systemHotspots = processedResult.systemHotspots();
            if (!systemHotspots.isEmpty()) {
                content.append("<details><summary>").append(I18nService.tr("系统/核心耗时")).append("</summary>\n\n");
                content.append("| ").append(I18nService.tr("来源")).append(" | ").append(I18nService.tr("采样占比")).append(" |\n|------|----------|\n");
                for (var entry : systemHotspots.entrySet()) {
                    content.append("| ").append(entry.getKey()).append(" | ").append(String.format("%.2f%%", entry.getValue())).append(" |\n");
                }
                content.append("\n</details>\n\n");
            }

            // 热点方法触发路径（折叠，供追溯完整触发链路）
            content.append("<details><summary>").append(I18nService.tr("Top 热点方法触发路径")).append("</summary>\n\n");
            content.append("| ").append(I18nService.tr("来源")).append(" | ").append(I18nService.tr("方法")).append(" | ").append(I18nService.tr("自身耗时")).append(" | ").append(I18nService.tr("触发路径")).append(" |\n|------|------|------|--------|\n");
            for (var hotspot : processedResult.hotspots()) {
                content.append("| ").append(hotspot.pluginName()).append(" | ").append(hotspot.className()).append(".").append(hotspot.methodName()).append(" | ").append(String.format("%.2f%%", hotspot.percentage())).append(" | ");
                if (!hotspot.callChain().isEmpty()) {
                    content.append(hotspot.callChain());
                } else {
                    content.append("-");
                }
                content.append(" |\n");
            }
            content.append("\n</details>\n\n");
        } else {
            content.append(I18nService.tr("未获取到 Profiler 解析数据。")).append("\n\n");
        }

        // AI 诊断结论
        content.append(I18nService.tr("## 3. AI 诊断结论")).append("\n\n");
        if (aiDiagnosis != null && !aiDiagnosis.isEmpty()) {
            content.append(aiDiagnosis).append("\n");
        } else {
            content.append(I18nService.tr("AI 诊断未执行或返回为空。")).append("\n");
        }

        content.append("\n---\n\n");
        content.append("*报告结束 / End of Report*\n");

        // 写入文件
        try {
            Files.writeString(reportFile.toPath(), content.toString(), StandardCharsets.UTF_8);
            PluginLoggerUtil.info(LOG_PREFIX, "诊断报告已生成: {}", reportFile.getAbsolutePath());
            return reportFile;
        } catch (IOException e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("无法写入诊断报告: {}", e.getMessage()), e);
            return null;
        }
    }

    /**
     * 从 Spark ?raw=1 元数据 JSON 中提取服务器状态概览
     */
    private String extractMetadataSummary(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return I18nService.tr("无元数据");
        }

        try {
            var root = JsonParser.parseString(metadataJson).getAsJsonObject();

            // 防御 JsonNull：Gson 的 getAsJsonObject() 遇到 JSON null 值会抛 ClassCastException
            // 必须先通过 isJsonObject() 确认值为 JSON 对象再获取
            if (!root.has("metadata") || !root.get("metadata").isJsonObject()) {
                return I18nService.tr("元数据中无 metadata 节点");
            }
            var metadata = root.getAsJsonObject("metadata");

            if (!metadata.has("platformStatistics") || !metadata.get("platformStatistics").isJsonObject()) {
                return I18nService.tr("元数据中无 platformStatistics 节点");
            }
            var platformStats = metadata.getAsJsonObject("platformStatistics");

            StringBuilder sb = new StringBuilder();

            // TPS
            if (platformStats.has("tps") && platformStats.get("tps").isJsonObject()) {
                var tps = platformStats.getAsJsonObject("tps");
                sb.append(I18nService.tr("### 1.2 性能指标（采样期间）")).append("\n\n");
                sb.append("| ").append(I18nService.tr("指标")).append(" | ").append(I18nService.tr("1分钟")).append(" | ").append(I18nService.tr("5分钟")).append(" | ").append(I18nService.tr("15分钟")).append(" |\n|------|-------|-------|--------|\n");
                sb.append("| TPS  | ").append(JsonSafeGetUtil.fmtDouble(tps, "last1m")).append(" | ").append(JsonSafeGetUtil.fmtDouble(tps, "last5m")).append(" | ").append(JsonSafeGetUtil.fmtDouble(tps, "last15m")).append(" |\n\n");
            }

            // MSPT + 延迟
            if (platformStats.has("mspt") && platformStats.get("mspt").isJsonObject()) {
                var mspt = platformStats.getAsJsonObject("mspt");
                var last1m = mspt.has("last1m") && mspt.get("last1m").isJsonObject() ? mspt.getAsJsonObject("last1m") : null;
                if (last1m != null) {
                    sb.append("| ").append(I18nService.tr("指标")).append(" | ").append(I18nService.tr("均值")).append(" | ").append(I18nService.tr("最大值")).append(" | P95 |\n|------|------|--------|-----|\n");
                    sb.append("| MSPT | ").append(JsonSafeGetUtil.fmtDouble(last1m, "mean")).append("ms").append(" | ").append(JsonSafeGetUtil.fmtDouble(last1m, "max")).append("ms").append(" | ").append(JsonSafeGetUtil.fmtDouble(last1m, "percentile95")).append("ms").append(" |\n");

                    // Ping 合并到同一表格（防御 ping=null）
                    if (platformStats.has("ping") && platformStats.get("ping").isJsonObject()) {
                        var ping = platformStats.getAsJsonObject("ping");
                        var ping15m = ping.has("last15m") && ping.get("last15m").isJsonObject() ? ping.getAsJsonObject("last15m") : null;
                        if (ping15m != null) {
                            sb.append("| ").append(I18nService.tr("延迟")).append(" | ").append(JsonSafeGetUtil.fmtDouble(ping15m, "mean")).append("ms").append(" | ").append(JsonSafeGetUtil.fmtDouble(ping15m, "max")).append("ms").append(" | —").append(" |\n");
                        }
                    }
                    sb.append("\n");
                }
            }

            // 内存（Spark 元数据，与其他指标数据源一致）
            if (platformStats.has("memory") && platformStats.get("memory").isJsonObject()) {
                var mem = platformStats.getAsJsonObject("memory");
                if (mem.has("heap") && mem.get("heap").isJsonObject()) {
                    var heap = mem.getAsJsonObject("heap");
                    String used = JsonSafeGetUtil.fmtMemLong(heap, "used");
                    String max = JsonSafeGetUtil.fmtMemLong(heap, "max");
                    if (!("N/A".equals(used) && "N/A".equals(max))) {
                        sb.append(I18nService.tr("**JVM 堆内存**: {}", used + " / " + max)).append("\n\n");
                    }
                }
            } else if (platformStats.has("ping") && platformStats.get("ping").isJsonObject()) {
                // 无 MSPT 但有 Ping 时独立显示
                var ping = platformStats.getAsJsonObject("ping");
                var ping15m = ping.has("last15m") && ping.get("last15m").isJsonObject() ? ping.getAsJsonObject("last15m") : null;
                if (ping15m != null) {
                    sb.append("| ").append(I18nService.tr("延迟指标")).append(" | ").append(I18nService.tr("均值")).append(" | ").append(I18nService.tr("最大值")).append(" |\n|----------|------|--------|\n");
                    sb.append("| ").append(I18nService.tr("延迟")).append(" | ").append(JsonSafeGetUtil.fmtDouble(ping15m, "mean")).append("ms").append(" | ").append(JsonSafeGetUtil.fmtDouble(ping15m, "max")).append("ms").append(" |\n\n");
                }
            }

            // 服务器概况：玩家 + 实体
            if (platformStats.has("playerCount")) {
                sb.append(I18nService.tr("### 1.3 服务器概况")).append("\n\n");
                sb.append(I18nService.tr("**在线玩家**: {}", platformStats.get("playerCount").getAsInt()));
                if (platformStats.has("world") && platformStats.get("world").isJsonObject()) {
                    sb.append("  |  ").append(I18nService.tr("**总实体数**: {}", JsonSafeGetUtil.fmtInt(platformStats.getAsJsonObject("world"), "totalEntities")));
                }
                sb.append("\n\n");
            }

            // 实体类型 Top 10
            if (platformStats.has("world") && platformStats.get("world").isJsonObject()) {
                var world = platformStats.getAsJsonObject("world");

                if (world.has("entityCounts") && world.get("entityCounts").isJsonObject()) {
                    var counts = world.getAsJsonObject("entityCounts");
                    var sorted = counts.entrySet().stream().sorted(Map.Entry.comparingByValue((a, b) -> Double.compare(b.getAsDouble(), a.getAsDouble()))).limit(10).toList();
                    sb.append("| ").append(I18nService.tr("实体类型")).append(" | ").append(I18nService.tr("数量")).append(" |\n|----------|------|\n");
                    for (var entry : sorted) {
                        sb.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().getAsInt()).append(" |\n");
                    }
                    sb.append("\n");
                }

                // 各世界实体详情 + 高密度区块（折叠展示）
                if (world.has("worlds") && world.get("worlds").isJsonArray()) {
                    var worlds = world.getAsJsonArray("worlds");
                    StringBuilder worldsContent = new StringBuilder();

                    // 世界概览表格（按实体数降序）
                    worldsContent.append("| ").append(I18nService.tr("世界")).append(" | ").append(I18nService.tr("实体数")).append(" |\n|------|--------|\n");
                    var worldList = new ArrayList<JsonObject>();
                    for (var elem : worlds) {
                        worldList.add(elem.getAsJsonObject());
                    }
                    worldList.sort((a, b) -> {
                        int aTotal = a.has("totalEntities") ? a.get("totalEntities").getAsInt() : 0;
                        int bTotal = b.has("totalEntities") ? b.get("totalEntities").getAsInt() : 0;
                        return Integer.compare(bTotal, aTotal);
                    });
                    for (var w : worldList) {
                        String worldName = w.get("name").getAsString();
                        int worldTotal = w.has("totalEntities") ? w.get("totalEntities").getAsInt() : 0;
                        worldsContent.append("| ").append(worldName).append(" | ").append(worldTotal).append(" |\n");
                    }
                    worldsContent.append("\n");

                    // 各世界高密度区块
                    for (var elem : worlds) {
                        var w = elem.getAsJsonObject();
                        String worldName = w.get("name").getAsString();

                        var hotChunks = new ArrayList<Map.Entry<int[], Integer>>();
                        if (w.has("regions")) {
                            for (var regionElem : w.getAsJsonArray("regions")) {
                                var region = regionElem.getAsJsonObject();
                                if (!region.has("chunks")) continue;
                                for (var chunkElem : region.getAsJsonArray("chunks")) {
                                    var chunk = chunkElem.getAsJsonObject();
                                    if (!chunk.has("totalEntities")) continue;
                                    int chunkTotal = chunk.get("totalEntities").getAsInt();
                                    if (chunkTotal <= 2) continue;
                                    hotChunks.add(Map.entry(new int[]{chunk.get("x").getAsInt(), chunk.get("z").getAsInt()}, chunkTotal));
                                }
                            }
                        }

                        if (!hotChunks.isEmpty()) {
                            hotChunks.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                            int limit = Math.min(hotChunks.size(), 5);
                            worldsContent.append("**").append(worldName).append("** ").append(I18nService.tr("高密度区块")).append(":\n\n");
                            worldsContent.append("| ").append(I18nService.tr("区块坐标 (x, z)")).append(" | ").append(I18nService.tr("实体数")).append(" |\n|------------------|--------|\n");
                            for (int i = 0; i < limit; i++) {
                                var hc = hotChunks.get(i);
                                // 区块坐标（乘以16为方块坐标起点，服主可 /tp x ~ z 定位）
                                int blockX = hc.getKey()[0] * 16;
                                int blockZ = hc.getKey()[1] * 16;
                                worldsContent.append("| (").append(blockX).append(", ").append(blockZ).append(") | ").append(hc.getValue()).append(" |\n");
                            }
                            worldsContent.append("\n");
                        }
                    }

                    // 如果有世界数据，用折叠展示
                    if (!worldsContent.isEmpty()) {
                        sb.append("<details><summary>").append(I18nService.tr("各世界实体分布与高密度区块")).append("</summary>\n\n");
                        sb.append(worldsContent);
                        sb.append("</details>\n\n");
                    }
                }
            }

            return !sb.isEmpty() ? sb.toString() : I18nService.tr("元数据中无可用状态信息");
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_PREFIX, "解析元数据 JSON 失败: {}", e.getMessage());
            return I18nService.tr("解析失败: {}", e.getMessage());
        }
    }

    /**
     * 在报告中追加服务器活动指标（玩家移动距离 + 区块加载变化）
     */
    private void appendActivityMetrics(StringBuilder content, ServerActivitySnapshot before, ServerActivitySnapshot after) {
        if (before.worldChunkCounts().isEmpty() && after.worldChunkCounts().isEmpty() && before.playerBlockCoords().isEmpty() && after.playerBlockCoords().isEmpty()) {
            return;
        }

        content.append(I18nService.tr("### 服务器活动指标（采样窗口）")).append("\n\n");

        // 玩家移动距离（主要指标：直接反映采样窗口内的玩家活动强度）
        Map<String, int[]> beforeCoords = before.playerBlockCoords();
        Map<String, int[]> afterCoords = after.playerBlockCoords();
        if (!afterCoords.isEmpty()) {
            content.append("| ").append(I18nService.tr("玩家")).append(" | ").append(I18nService.tr("移动距离")).append(" | ").append(I18nService.tr("世界 (区块x, 区块z)")).append(" |\n");
            content.append("|------|---------|-----------------|\n");
            for (var entry : after.playerLocations().entrySet()) {
                String name = entry.getKey();
                String pos = entry.getValue();
                String distance = I18nService.tr("无变化");

                int[] bc = beforeCoords.get(name);
                int[] ac = afterCoords.get(name);
                if (bc != null && ac != null) {
                    double dist = Math.sqrt(Math.pow(ac[0] - bc[0], 2) + Math.pow(ac[1] - bc[1], 2));
                    if (dist < 1) {
                        distance = I18nService.tr("静止");
                    } else {
                        distance = (int) dist + " " + I18nService.tr("格");
                    }
                } else {
                    // 采样期间加入或不在 before 快照中
                    distance = "-";
                }
                content.append("| ").append(name).append(" | ").append(distance).append(" | ").append(pos).append(" |\n");
            }
            content.append("\n");
        }

        // 区块加载变化（折叠展示：净增量仅供参考，不直接反映活动强度）
        LinkedHashSet<String> allWorlds = new LinkedHashSet<>();
        allWorlds.addAll(before.worldChunkCounts().keySet());
        allWorlds.addAll(after.worldChunkCounts().keySet());

        boolean hasChunkChange = false;
        for (String world : allWorlds) {
            int beforeChunks = before.worldChunkCounts().getOrDefault(world, 0);
            int afterChunks = after.worldChunkCounts().getOrDefault(world, 0);
            if (beforeChunks != afterChunks) {
                hasChunkChange = true;
                break;
            }
        }

        if (hasChunkChange) {
            content.append("<details><summary>").append(I18nService.tr("区块加载变化")).append("</summary>\n\n");
            content.append("| ").append(I18nService.tr("世界")).append(" | ").append(I18nService.tr("采样前")).append(" | ").append(I18nService.tr("采样后")).append(" | ").append(I18nService.tr("增量")).append(" |\n");
            content.append("|------|------|------|------|\n");
            for (String world : allWorlds) {
                int beforeChunks = before.worldChunkCounts().getOrDefault(world, 0);
                int afterChunks = after.worldChunkCounts().getOrDefault(world, 0);
                int diff = afterChunks - beforeChunks;
                String diffStr = diff != 0 ? (diff > 0 ? "+" : "") + diff : "0";
                content.append("| ").append(world).append(" | ").append(beforeChunks).append(" | ").append(afterChunks).append(" | ").append(diffStr).append(" |\n");
            }
            content.append("\n</details>\n\n");
        }
    }

    /**
     * 构建 Kilacraft-AI 自监控文本（IO 线程池 + DB 连接池状态）
     *
     * <p>公共方法，供 ServerHealthGuardian（AI 提示词）和报告生成共用。</p>
     */
    public static void appendSelfMonitoring(StringBuilder sb) {
        KilacraftAI plugin = KilacraftAI.getInstance();

        // IO 线程池状态
        try {
            var ioPool = (ThreadPoolExecutor) FoliaCompat.getIOPool();
            sb.append(I18nService.tr("IO 线程池: 活跃={}, 最大={}, 队列={}, 已完成={}", ioPool.getActiveCount(), ioPool.getMaximumPoolSize(), ioPool.getQueue().size(), ioPool.getCompletedTaskCount())).append("\n");
        } catch (Exception e) {
            sb.append(I18nService.tr("IO 线程池: 获取失败 ({})", e.getMessage())).append("\n");
        }

        // DB 连接池状态
        try {
            String poolInfo = plugin.getDatabaseManager().getPoolInfo();
            sb.append(I18nService.tr("DB 连接池: {}", poolInfo)).append("\n");
        } catch (Exception e) {
            sb.append(I18nService.tr("DB 连接池: 获取失败 ({})", e.getMessage())).append("\n");
        }
    }

}
