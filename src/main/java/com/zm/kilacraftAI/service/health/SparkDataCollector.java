package com.zm.kilacraftAI.service.health;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.gc.GarbageCollector;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark 数据采集封装
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class SparkDataCollector {

    private static final String LOG_PREFIX = "健康监控";

    /**
     * 缓存的 Spark 实例（仅当 Spark 插件存在时非 null）
     */
    private Spark spark;

    /**
     * MSPT 10s 窗口连续确认状态
     *
     * <p>记录连续超过阈值的轮询次数。达到配置值（默认 3 次）时正式触发告警。</p>
     */
    private int mspt10sConsecutiveCount = 0;

    /**
     * CPU process 连续确认状态
     *
     * <p>与 mspt10sConsecutiveCount 对称：记录连续超过阈值的轮询次数，
     * 达到配置值（默认 3 次）时正式触发告警。恢复正常即清零。</p>
     */
    private int cpuConsecutiveCount = 0;

    /**
     * 检查 Spark 是否可用
     *
     * @return true 表示 Spark 已安装且 API 可用
     */
    public boolean isSparkAvailable() {
        return getSpark() != null;
    }

    /**
     * 采集完整的服务器健康快照
     *
     * <p>包含 TPS、MSPT、CPU、GC 等全部指标。
     * 各指标采集独立 try-catch，单项失败不影响其他指标。</p>
     *
     * @return 健康快照（不可变），任何采集失败的字段为 null/-1
     */
    public HealthSnapshot collectSnapshot() {
        try {
            Spark spark = getSpark();
            if (spark == null) return emptySnapshot();

            // TPS → DoubleStatistic<TicksPerSecond>
            Double tps5s = null, tps1m = null, tps5m = null;
            try {
                DoubleStatistic<StatisticWindow.TicksPerSecond> tpsStat = spark.tps();
                if (tpsStat != null) {
                    tps5s = tpsStat.poll(StatisticWindow.TicksPerSecond.SECONDS_5);
                    tps1m = tpsStat.poll(StatisticWindow.TicksPerSecond.MINUTES_1);
                    tps5m = tpsStat.poll(StatisticWindow.TicksPerSecond.MINUTES_5);
                }
            } catch (Exception e) {
                PluginLoggerUtil.debug(LOG_PREFIX, "TPS 采集异常: {}", e.getMessage());
            }

            // MSPT → GenericStatistic<DoubleAverageInfo, MillisPerTick>
            // 双窗口采集：10s 窗口高灵敏度（捕捉突发尖峰），1m 窗口平滑趋势
            double msptMax = -1, msptMedian = -1, msptP95 = -1;
            double mspt10sMax = -1, mspt10sMedian = -1;
            try {
                GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> msptStat = spark.mspt();
                if (msptStat != null) {
                    DoubleAverageInfo info1m = msptStat.poll(StatisticWindow.MillisPerTick.MINUTES_1);
                    if (info1m != null) {
                        msptMax = info1m.max();
                        msptMedian = info1m.median();
                        msptP95 = info1m.percentile95th();
                    }
                    DoubleAverageInfo info10s = msptStat.poll(StatisticWindow.MillisPerTick.SECONDS_10);
                    if (info10s != null) {
                        mspt10sMax = info10s.max();
                        mspt10sMedian = info10s.median();
                    }
                }
            } catch (Exception e) {
                PluginLoggerUtil.debug(LOG_PREFIX, "MSPT 采集异常: {}", e.getMessage());
            }

            // CPU → DoubleStatistic<CpuUsage>
            Double cpuProcess = null, cpuSystem = null;
            try {
                DoubleStatistic<StatisticWindow.CpuUsage> cpuProcessStat = spark.cpuProcess();
                cpuProcess = cpuProcessStat.poll(StatisticWindow.CpuUsage.MINUTES_1);
                DoubleStatistic<StatisticWindow.CpuUsage> cpuSystemStat = spark.cpuSystem();
                cpuSystem = cpuSystemStat.poll(StatisticWindow.CpuUsage.MINUTES_1);
            } catch (Exception e) {
                PluginLoggerUtil.debug(LOG_PREFIX, "CPU 采集异常: {}", e.getMessage());
            }

            // GC
            Map<String, GcInfo> gcInfo = collectGcInfo(spark);

            return new HealthSnapshot(tps5s, tps1m, tps5m, msptMax, msptMedian, msptP95, mspt10sMax, mspt10sMedian, cpuProcess != null ? cpuProcess : -1, cpuSystem != null ? cpuSystem : -1, gcInfo);
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("采集 Spark 快照失败: {}", e.getMessage()), e);
            return emptySnapshot();
        }
    }

    /**
     * 检查各指标是否超过阈值
     *
     * @param snapshot                 当前快照
     * @param thresholds               告警阈值配置
     * @param msptConsecutiveThreshold MSPT max 连续确认次数
     * @param cpuConsecutiveThreshold  CPU process 连续确认次数
     * @return 告警列表（空表示正常）
     */
    public List<String> checkThresholds(HealthSnapshot snapshot, Map<String, Double> thresholds, int msptConsecutiveThreshold, int cpuConsecutiveThreshold) {
        List<String> alerts = new ArrayList<>();

        // TPS 1m < threshold
        Double tpsThreshold = thresholds.getOrDefault("tps_threshold", 15.0);
        if (snapshot.tps1m() != null && snapshot.tps1m() < tpsThreshold) {
            alerts.add("TPS 1m=" + String.format("%.1f", snapshot.tps1m()) + " < " + tpsThreshold.intValue());
        }

        // MSPT max — median 门控：max 超标且 median(10s) 超标才计数
        // max 阈值保持单 tick 预算 50ms（均匀型卡顿 max≈median≈70ms，抬高即漏报）
        // median(10s) 区分单 tick 尖峰与健康服微抖动；不可用时（老 Spark 无 percentile）回退旧行为避免新增漏报
        Double msptMaxThreshold = thresholds.getOrDefault("mspt_max_threshold", 50.0);
        Double msptMedianThreshold = thresholds.getOrDefault("mspt_median_threshold", 60.0);
        boolean has10sData = snapshot.mspt10sMax() > 0;
        double effectiveMax = has10sData ? snapshot.mspt10sMax() : snapshot.msptMax();
        // 门控：median 可用时要求超标；不可用时放行
        boolean medianGatePassed = !(snapshot.mspt10sMedian() > 0) || snapshot.mspt10sMedian() > msptMedianThreshold;
        String maxLabel = has10sData ? "MSPT max(10s)" : "MSPT max";
        if (effectiveMax > msptMaxThreshold && medianGatePassed) {
            mspt10sConsecutiveCount++;
            if (!has10sData || mspt10sConsecutiveCount >= msptConsecutiveThreshold) {
                // 确认触发
                String medianInfo = snapshot.mspt10sMedian() > 0 ? " (median=" + String.format("%.1f", snapshot.mspt10sMedian()) + "ms > " + msptMedianThreshold.intValue() + "ms)" : "";
                alerts.add(maxLabel + "=" + String.format("%.1f", effectiveMax) + "ms > " + msptMaxThreshold.intValue() + "ms" + medianInfo);
                mspt10sConsecutiveCount = 0;
            } else {
                // 尚未达到连续确认阈值
                PluginLoggerUtil.debug(LOG_PREFIX, "MSPT 10s max={}ms 超过阈值，连续确认 {}/{}", String.format("%.1f", effectiveMax), mspt10sConsecutiveCount, msptConsecutiveThreshold);
            }
        } else {
            // 恢复正常或门控未通过，重置连续计数
            if (mspt10sConsecutiveCount > 0) {
                PluginLoggerUtil.debug(LOG_PREFIX, "MSPT 10s max 已恢复正常，连续计数 {} → 0", mspt10sConsecutiveCount);
            }
            mspt10sConsecutiveCount = 0;
        }

        // MSPT p95 — 1m 窗口（p95 是 1200 tick 的 95 百分位，对 3 秒尖峰不敏感，无需连续确认）
        Double msptP95Threshold = thresholds.getOrDefault("mspt_p95_threshold", 50.0);
        if (snapshot.msptP95() > 0 && snapshot.msptP95() > msptP95Threshold) {
            alerts.add("MSPT p95=" + String.format("%.1f", snapshot.msptP95()) + "ms > " + msptP95Threshold.intValue() + "ms");
        }

        // CPU process — 阈值 + 连续确认（双防护）
        // cpuProcess 是占整机全部核心比例；阈值 90 + 连续 3 次过滤日常波动，仅真实持续打满才告警
        Double cpuThreshold = thresholds.getOrDefault("cpu_threshold", 90.0);
        if (snapshot.cpuProcess() > 0 && snapshot.cpuProcess() > cpuThreshold) {
            cpuConsecutiveCount++;
            if (cpuConsecutiveCount >= cpuConsecutiveThreshold) {
                alerts.add("CPU process=" + String.format("%.1f", snapshot.cpuProcess()) + "% > " + cpuThreshold.intValue() + "%");
                cpuConsecutiveCount = 0;
            } else {
                PluginLoggerUtil.debug(LOG_PREFIX, "CPU {}% 超过阈值，连续确认 {}/{}", String.format("%.1f", snapshot.cpuProcess()), cpuConsecutiveCount, cpuConsecutiveThreshold);
            }
        } else {
            if (cpuConsecutiveCount > 0) {
                PluginLoggerUtil.debug(LOG_PREFIX, "CPU 已恢复正常，连续计数 {} → 0", cpuConsecutiveCount);
            }
            cpuConsecutiveCount = 0;
        }

        return alerts;
    }

    /**
     * 获取 Spark 实例（懒加载 + 缓存）
     *
     * <p>通过 {@link SparkProvider#get()} 获取，失败返回 null。
     * 仅在首次调用或之前获取失败时尝试重新获取。</p>
     *
     * @return Spark 实例，获取失败返回 null
     */
    private Spark getSpark() {
        if (spark != null) return spark;
        try {
            spark = SparkProvider.get();
        } catch (Exception e) {
            PluginLoggerUtil.debug(LOG_PREFIX, "Spark 实例获取失败: {}", e.getMessage());
        }
        return spark;
    }

    /**
     * 采集 GC 信息
     */
    private Map<String, GcInfo> collectGcInfo(Spark spark) {
        try {
            Map<String, GarbageCollector> gcMap = spark.gc();
            if (gcMap.isEmpty()) return Map.of();

            Map<String, GcInfo> result = new LinkedHashMap<>();
            for (Map.Entry<String, GarbageCollector> entry : gcMap.entrySet()) {
                GarbageCollector gc = entry.getValue();
                result.put(entry.getKey(), new GcInfo(gc.totalCollections(), gc.totalTime(), gc.avgTime(), gc.avgFrequency()));
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private HealthSnapshot emptySnapshot() {
        return new HealthSnapshot(null, null, null, -1, -1, -1, -1, -1, -1, -1, Map.of());
    }

    /**
     * 服务器健康快照（不可变记录）
     *
     * @param tps5s         TPS 5秒窗口
     * @param tps1m         TPS 1分钟窗口
     * @param tps5m         TPS 5分钟窗口
     * @param msptMax       MSPT 最大值（1m 窗口）
     * @param msptMedian    MSPT 中位数（1m 窗口）
     * @param msptP95       MSPT 95百分位（1m 窗口）
     * @param mspt10sMax    MSPT 最大值（10s 窗口，高灵敏度）
     * @param mspt10sMedian MSPT 中位数（10s 窗口，用于 max 门控——区分单 tick 尖峰与持续卡顿）
     * @param cpuProcess    进程级 CPU 使用率
     * @param cpuSystem     系统级 CPU 使用率
     * @param gcInfo        GC 信息（按名称分组）
     */
    public record HealthSnapshot(Double tps5s, Double tps1m, Double tps5m, double msptMax, double msptMedian,
                                 double msptP95, double mspt10sMax, double mspt10sMedian, double cpuProcess,
                                 double cpuSystem, Map<String, GcInfo> gcInfo) {
        /**
         * 判断是否存在任何有效数据
         */
        public boolean hasData() {
            return tps1m != null || msptP95 > 0 || cpuProcess > 0;
        }
    }

    /**
     * GC 信息记录
     */
    public record GcInfo(long totalCollections, long totalTime, double avgTime, long avgFrequency) {
    }

}
