package com.zm.kilacraftAI.skills.admin;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.db.dao.SkillLogDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 审计日志查询技能
 *
 * <p>查看 AI 技能的执行记录，包括谁用了什么技能、参数是什么、是否成功、耗时多久。
 * 支持按玩家、技能名、时间范围、成功/失败筛选。仅限管理员使用。</p>
 *
 * <p>数据源为项目已有的 kca_skill_log 表，与 Spark 无关。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class AuditLogSkill implements Skill {

    private static final String LOG_PREFIX = "审计日志";
    private final SkillConfigManager configManager;

    public AuditLogSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig("admin", "AuditLogSkill") == null) {
            configManager.saveDefaultSkillConfig("admin", "AuditLogSkill");
            configManager.loadSingleSkillConfig("admin", "AuditLogSkill");
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig("admin", "AuditLogSkill") : null;
    }

    @Override
    public String getName() {
        return "audit_log";
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
        return PluginPermissionEnum.ADMIN_AUDIT.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        if (action == null || action.isEmpty()) {
            action = "query_logs";
        }

        return switch (action) {
            case "query_logs" -> executeQueryLogs(context);
            case "skill_stats" -> executeSkillStats(context);
            case "error_logs" -> executeErrorLogs(context);
            default -> SkillResult.failure(I18nService.tr("未知的动作: {}", action)).toFuture();
        };
    }

    /**
     * 查询技能执行日志
     */
    private CompletableFuture<SkillResult> executeQueryLogs(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String playerName = context.getEntity("player_name");
            String skillName = context.getEntity("skill_name");
            String limitStr = context.getEntity("limit");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 20);

            long[] range = AdminSkillUtil.parseTimeRangeFull(timeRange);
            if (range == null) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            // 如果提供了玩家名，需要先查 UUID
            String playerUuid = null;
            if (playerName != null && !playerName.isEmpty()) {
                DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
                try (Connection conn = dbManager.getConnection()) {
                    PlayerProfileDao profileDao = new PlayerProfileDao(dbManager.getTablePrefix());
                    var profile = profileDao.loadByName(conn, playerName);
                    if (profile != null) {
                        playerUuid = profile.getUuid().toString();
                    } else {
                        return SkillResult.success(I18nService.tr("未找到玩家: {}", playerName));
                    }
                }
            }

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                SkillLogDao dao = new SkillLogDao(dbManager.getTablePrefix());
                List<SkillLogDao.SkillLogEntry> logs = dao.queryLogs(conn, range[0], range[1], playerUuid, skillName, null, limit);

                List<Map<String, Object>> formatted = formatLogEntries(logs);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("count", logs.size());
                data.put("logs", formatted);

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                // UUID → 玩家名反查
                Map<String, String> nameCache = new HashMap<>();

                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("全服技能执行日志（{}，共 {} 条）", timeRange, logs.size()));
                for (SkillLogDao.SkillLogEntry entry : logs) {
                    String pName = AdminSkillUtil.resolvePlayerName(entry.playerUuid(), conn, nameCache);
                    String timeStr = AdminSkillUtil.formatTimestamp(entry.createdAt());
                    msg.append("\n  - ").append(timeStr).append(" ").append(pName).append(" ").append(entry.skillName()).append("/").append(entry.action()).append(entry.success() ? I18nService.tr(" 成功") : I18nService.tr(" 失败")).append(" (").append(entry.executionMs()).append("ms)");
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询技能日志失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询技能日志失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 技能使用统计排行
     */
    private CompletableFuture<SkillResult> executeSkillStats(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String limitStr = context.getEntity("limit");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 10);

            long[] range = AdminSkillUtil.parseTimeRangeFull(timeRange);
            if (range == null) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                SkillLogDao dao = new SkillLogDao(dbManager.getTablePrefix());
                List<SkillLogDao.SkillUsageStat> stats = dao.queryUsageStats(conn, range[0], range[1], limit);

                int totalExecutions = stats.stream().mapToInt(SkillLogDao.SkillUsageStat::totalCount).sum();
                int totalSuccess = stats.stream().mapToInt(SkillLogDao.SkillUsageStat::successCount).sum();
                int totalFail = stats.stream().mapToInt(SkillLogDao.SkillUsageStat::failCount).sum();
                double successRate = totalExecutions > 0 ? Math.round((double) totalSuccess / totalExecutions * 1000.0) / 10.0 : 0.0;

                List<Map<String, Object>> formatted = formatUsageStats(stats);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("total_executions", totalExecutions);
                data.put("total_success", totalSuccess);
                data.put("total_fail", totalFail);
                data.put("success_rate", successRate);
                data.put("stats", formatted);

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("全服技能使用统计（{}，共 {} 次执行，成功率 {}%）", timeRange, totalExecutions, successRate));
                for (Map<String, Object> row : formatted) {
                    double rowRate = (double) row.get("success_rate");
                    msg.append("\n  - ").append(row.get("skill_name")).append("/").append(row.get("action")).append(": ").append(row.get("total_count")).append(I18nService.tr("次")).append(" (").append(I18nService.tr("成功")).append(" ").append(row.get("success_count")).append(", ").append(I18nService.tr("失败")).append(" ").append(row.get("fail_count")).append(", ").append(I18nService.tr("成功率")).append(" ").append(String.format("%.1f", rowRate)).append("%)").append(" ").append(I18nService.tr("均耗")).append(" ").append(row.get("avg_duration_ms")).append("ms");
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询技能统计失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询技能统计失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 失败执行日志
     */
    private CompletableFuture<SkillResult> executeErrorLogs(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String skillName = context.getEntity("skill_name");
            String limitStr = context.getEntity("limit");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 20);

            long[] range = AdminSkillUtil.parseTimeRangeFull(timeRange);
            if (range == null) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                SkillLogDao dao = new SkillLogDao(dbManager.getTablePrefix());
                List<SkillLogDao.SkillLogEntry> logs = dao.queryErrorLogs(conn, range[0], range[1], skillName, limit);

                List<Map<String, Object>> formatted = formatLogEntries(logs);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("count", logs.size());
                data.put("logs", formatted);

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                Map<String, String> nameCache = new HashMap<>();

                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("全服失败执行日志（{}，共 {} 条）", timeRange, logs.size()));
                for (SkillLogDao.SkillLogEntry entry : logs) {
                    String pName = AdminSkillUtil.resolvePlayerName(entry.playerUuid(), conn, nameCache);
                    String timeStr = AdminSkillUtil.formatTimestamp(entry.createdAt());
                    String resultMsg = truncate(entry.resultMessage(), 100);
                    msg.append("\n  - ").append(timeStr).append(" ").append(pName).append(" ").append(entry.skillName()).append("/").append(entry.action()).append(" (").append(entry.executionMs()).append("ms)").append(": ").append(resultMsg != null ? resultMsg : "");
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询失败日志失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询失败日志失败", e);
            }
        }, LOG_PREFIX);
    }

    private List<Map<String, Object>> formatLogEntries(List<SkillLogDao.SkillLogEntry> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillLogDao.SkillLogEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("player_uuid", e.playerUuid());
            row.put("skill_name", e.skillName());
            row.put("action", e.action());
            row.put("success", e.success());
            row.put("result_message", truncate(e.resultMessage(), 200));
            row.put("execution_ms", e.executionMs());
            row.put("source", e.source());
            row.put("created_at", e.createdAt());
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> formatUsageStats(List<SkillLogDao.SkillUsageStat> stats) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillLogDao.SkillUsageStat s : stats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill_name", s.skillName());
            row.put("action", s.action());
            row.put("total_count", s.totalCount());
            row.put("success_count", s.successCount());
            row.put("fail_count", s.failCount());
            row.put("success_rate", s.totalCount() > 0 ? Math.round((double) s.successCount() / s.totalCount() * 1000.0) / 10.0 : 0.0);
            row.put("avg_duration_ms", Math.round(s.avgDurationMs() * 10.0) / 10.0);
            result.add(row);
        }
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
