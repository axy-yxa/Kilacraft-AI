package com.zm.kilacraftAI.skills.admin;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao;
import com.zm.kilacraftAI.skill.Skill;
import com.zm.kilacraftAI.skill.SkillContext;
import com.zm.kilacraftAI.skill.SkillResult;
import com.zm.kilacraftAI.skill.SkillConfig;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家行为分析技能
 *
 * <p>提供在线人数趋势、活跃玩家排行、新玩家流入、
 * 画像分析覆盖率和社交图谱洞察。仅限管理员使用。</p>
 *
 * <p>数据源为项目已有的数据库表（kca_server_event、kca_player_profile、kca_social_relation），
 * 与 Spark 无关。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class PlayerAnalysisSkill implements Skill {

    private static final String LOG_PREFIX = "玩家分析";
    private final SkillConfigManager configManager;

    public PlayerAnalysisSkill() {
        this.configManager = SkillConfigManager.getInstance();
        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("admin", "PlayerAnalysisSkill") == null) {
            configManager.saveDefaultSkillConfig("admin", "PlayerAnalysisSkill");
            configManager.loadSingleSkillConfig("admin", "PlayerAnalysisSkill");
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig("admin", "PlayerAnalysisSkill") : null;
    }

    @Override
    public String getName() {
        return "player_analysis";
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
        return PluginPermissionEnum.ADMIN_PLAYER.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        if (action == null || action.isEmpty()) {
            action = "online_trend";
        }

        return switch (action) {
            case "online_trend" -> executeOnlineTrend(context);
            case "top_active" -> executeTopActive(context);
            case "new_players" -> executeNewPlayers(context);
            case "profile_coverage" -> executeProfileCoverage(context);
            case "social_insights" -> executeSocialInsights(context);
            default -> SkillResult.failure(I18nService.tr("未知的动作: {}", action)).toFuture();
        };
    }

    /**
     * 在线人数趋势 — LOGIN/LOGOUT 事件按时间桶聚合
     */
    private CompletableFuture<SkillResult> executeOnlineTrend(SkillContext context) {
        String timeRangeStr = context.getEntity("time_range");
        String groupByStr = context.getEntity("group_by");
        return AdminSkillUtil.executeAsync(() -> {
            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            String groupBy = (groupByStr == null || groupByStr.isEmpty()) ? "day" : groupByStr;

            long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
            if (afterTime < 0) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            long bucketMs = switch (groupBy) {
                case "hour" -> 3_600_000L;
                case "day" -> 86_400_000L;
                default -> 86_400_000L;
            };

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                ServerEventDao dao = new ServerEventDao(dbManager.getTablePrefix());
                List<ServerEventDao.TimeBucketCount> counts = dao.countEventsByTypeBetween(conn, List.of("PLAYER_LOGIN", "PLAYER_LOGOUT"), bucketMs, afterTime, System.currentTimeMillis());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("group_by", groupBy);
                data.put("buckets", formatBucketCounts(counts));
                data.put("total_records", counts.size());

                return SkillResult.success(I18nService.tr("在线趋势查询完成（{}，按{}聚合）", timeRange, groupBy), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询在线趋势失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询在线趋势失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 活跃玩家排行 — 按登录次数/在线时长/最近登录排序
     */
    private CompletableFuture<SkillResult> executeTopActive(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String orderByStr = context.getEntity("order_by");
            String limitStr = context.getEntity("limit");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            String orderBy = (orderByStr == null || orderByStr.isEmpty()) ? "login_count" : orderByStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 10);

            long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
            if (afterTime < 0) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                PlayerProfileDao dao = new PlayerProfileDao(dbManager.getTablePrefix());
                List<PlayerProfileDao.TopActivePlayer> players = dao.queryTopActive(conn, afterTime, orderBy, limit);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("order_by", orderBy);
                data.put("players", formatTopActivePlayers(players));
                data.put("count", players.size());

                String summary = I18nService.tr("活跃玩家排行（{}，按{}排序，Top {}）", timeRange, orderBy, limit);
                return SkillResult.success(summary, data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询活跃玩家失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询活跃玩家失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 新玩家流入 — PLAYER_FIRST_JOIN 事件按时间聚合
     */
    private CompletableFuture<SkillResult> executeNewPlayers(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String groupByStr = context.getEntity("group_by");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            String groupBy = (groupByStr == null || groupByStr.isEmpty()) ? "day" : groupByStr;

            long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
            if (afterTime < 0) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            long bucketMs = switch (groupBy) {
                case "hour" -> 3_600_000L;
                case "day" -> 86_400_000L;
                default -> 86_400_000L;
            };

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                ServerEventDao dao = new ServerEventDao(dbManager.getTablePrefix());
                List<ServerEventDao.TimeBucketCount> counts = dao.countEventsByTypeBetween(conn, List.of("PLAYER_FIRST_JOIN"), bucketMs, afterTime, System.currentTimeMillis());

                int totalNew = counts.stream().mapToInt(ServerEventDao.TimeBucketCount::count).sum();

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("group_by", groupBy);
                data.put("total_new_players", totalNew);
                data.put("buckets", formatBucketCounts(counts));

                return SkillResult.success(I18nService.tr("新玩家流入统计（{}，共 {} 人）", timeRange, totalNew), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询新玩家流入失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询新玩家流入失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 画像分析覆盖率 — 已分析/待分析/覆盖率百分比
     */
    private CompletableFuture<SkillResult> executeProfileCoverage(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = context.getEntity("time_range");
            String limitStr = context.getEntity("limit");

            String timeRange = (timeRangeStr == null || timeRangeStr.isEmpty()) ? "7d" : timeRangeStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 20);

            long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
            if (afterTime < 0) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                PlayerProfileDao dao = new PlayerProfileDao(dbManager.getTablePrefix());
                PlayerProfileDao.ProfileCoverageResult coverage = dao.queryProfileCoverage(conn, afterTime);
                List<PlayerProfileDao.TopActivePlayer> pending = dao.queryPendingAnalysis(conn, afterTime, limit);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("total_players", coverage.totalPlayers());
                data.put("analyzed_count", coverage.analyzedCount());
                data.put("recently_analyzed", coverage.recentlyAnalyzed());
                data.put("coverage_pct", Math.round(coverage.coveragePct() * 10.0) / 10.0);
                data.put("pending_analysis", formatTopActivePlayers(pending));

                String summary = String.format(I18nService.tr("画像覆盖率: %d/%d（%.1f%%%%），近期新分析 %d 人，待分析 %d 人"), coverage.analyzedCount(), coverage.totalPlayers(), coverage.coveragePct(), coverage.recentlyAnalyzed(), pending.size());
                return SkillResult.success(summary, data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询画像覆盖率失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询画像覆盖率失败", e);
            }
        }, LOG_PREFIX);
    }

    /**
     * 社交图谱洞察 — 社交网络密度、孤立玩家检测、Top 关系
     */
    private CompletableFuture<SkillResult> executeSocialInsights(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String limitStr = context.getEntity("limit");
            int limit = AdminSkillUtil.parseLimit(limitStr, 10);

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                SocialRelationDao socialDao = new SocialRelationDao(dbManager.getTablePrefix());

                List<SocialRelationDao.SocialStats> topConnected = socialDao.querySocialStats(conn, limit);
                int totalRelations = socialDao.countTotalRelations(conn);
                List<SocialRelationDao.IsolatedPlayer> isolated = socialDao.queryIsolatedPlayers(conn, 0, limit);

                // 计算平均关系强度
                double avgStrength = topConnected.stream().mapToDouble(SocialRelationDao.SocialStats::avgStrength).average().orElse(0.0);

                Map<String, Object> networkStats = new LinkedHashMap<>();
                networkStats.put("total_relations", totalRelations);
                networkStats.put("avg_strength", Math.round(avgStrength * 1000.0) / 1000.0);
                networkStats.put("isolated_count", isolated.size());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("network_stats", networkStats);
                data.put("top_connected", formatSocialStats(topConnected));
                data.put("isolated_players", formatIsolatedPlayers(isolated));

                String summary = String.format(I18nService.tr("社交网络: %d 条关系，平均强度 %.3f，%d 名孤立玩家"), totalRelations, avgStrength, isolated.size());
                return SkillResult.success(summary, data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询社交图谱失败: {}", e.getMessage()), e);
                return SkillResult.failure("查询社交图谱失败", e);
            }
        }, LOG_PREFIX);
    }

    private List<Map<String, Object>> formatBucketCounts(List<ServerEventDao.TimeBucketCount> counts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerEventDao.TimeBucketCount c : counts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time_bucket", c.timeBucket());
            row.put("event_type", c.eventType());
            row.put("count", c.count());
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> formatTopActivePlayers(List<PlayerProfileDao.TopActivePlayer> players) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlayerProfileDao.TopActivePlayer p : players) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", p.uuid().toString());
            row.put("name", p.name());
            row.put("login_count", p.loginCount());
            row.put("total_playtime_hours", Math.round(p.totalPlaytimeMs() / 3600000.0 * 10.0) / 10.0);
            row.put("last_login", p.lastLogin());
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> formatSocialStats(List<SocialRelationDao.SocialStats> stats) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SocialRelationDao.SocialStats s : stats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("player_uuid", s.playerUuid().toString());
            row.put("relation_count", s.relationCount());
            row.put("avg_strength", Math.round(s.avgStrength() * 1000.0) / 1000.0);
            row.put("max_strength", Math.round(s.maxStrength() * 1000.0) / 1000.0);
            row.put("total_interactions", s.totalInteractions());
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> formatIsolatedPlayers(List<SocialRelationDao.IsolatedPlayer> players) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SocialRelationDao.IsolatedPlayer p : players) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", p.uuid().toString());
            row.put("name", p.name());
            result.add(row);
        }
        return result;
    }
}
