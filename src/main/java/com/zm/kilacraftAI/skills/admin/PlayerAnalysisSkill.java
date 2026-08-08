package com.zm.kilacraftAI.skills.admin;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    private static final String SKILL_NAME = "player_analysis";
    private static final String LOG_PREFIX = "玩家分析";
    private final SkillConfigManager configManager;

    public PlayerAnalysisSkill() {
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
        return PluginPermissionEnum.ADMIN_PLAYER.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // 权限校验：player 为 null 视为无权限（正常调用路径必有在线 player）
        Player caller = context.getPlayer();
        if (caller == null || !PluginPermissionEnum.ADMIN_PLAYER.hasPermission(caller)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.ADMIN_PLAYER.getNode())));
        }

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
            case "player_relations" -> executePlayerRelations(context);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action)).toFuture();
        };
    }

    /**
     * 在线人数趋势 — LOGIN/LOGOUT 事件按时间桶聚合
     */
    private CompletableFuture<SkillResult> executeOnlineTrend(SkillContext context) {
        String timeRangeStr = SkillEntityHelper.getString(context, "time_range");
        String groupByStr = SkillEntityHelper.getString(context, "group_by");
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

                List<Map<String, Object>> formatted = formatBucketCounts(counts);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("group_by", groupBy);
                data.put("buckets", formatted);
                data.put("total_records", counts.size());

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("服务器在线趋势查询完成（{}，按{}聚合）", timeRange, groupBy));
                if (counts.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("该时间段内服务器无登录/登出记录"));
                } else {
                    // 按时间段桶分组，合并同桶的登录/登出数据
                    Map<Long, List<ServerEventDao.TimeBucketCount>> grouped = counts.stream().collect(Collectors.groupingBy(ServerEventDao.TimeBucketCount::timeBucket, LinkedHashMap::new, Collectors.toList()));
                    for (var entry : grouped.entrySet()) {
                        String dateStr = AdminSkillUtil.formatTimestamp(entry.getKey());
                        msg.append("\n  ").append(dateStr);
                        for (ServerEventDao.TimeBucketCount c : entry.getValue()) {
                            msg.append(" | ").append(AdminSkillUtil.translateEventType(c.eventType())).append(" ").append(c.count()).append(I18nService.tr("人"));
                        }
                    }
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询在线趋势失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询在线趋势失败: {}", e.getMessage()));
            }
        }, LOG_PREFIX);
    }

    /**
     * 活跃玩家排行 — 按登录次数/在线时长/最近登录排序
     */
    private CompletableFuture<SkillResult> executeTopActive(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = SkillEntityHelper.getString(context, "time_range");
            String orderByStr = SkillEntityHelper.getString(context, "order_by");
            String limitStr = SkillEntityHelper.getString(context, "limit");

            String timeRange = (timeRangeStr != null) ? timeRangeStr : "7d";
            String orderBy = (orderByStr == null || orderByStr.isEmpty()) ? "login_count" : orderByStr;
            int limit = AdminSkillUtil.parseLimit(limitStr, 10);

            long afterTime = AdminSkillUtil.parseTimeRange(timeRange);
            if (afterTime < 0) return SkillResult.failure(I18nService.tr("无效的时间范围: {}", timeRange));

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                PlayerProfileDao dao = new PlayerProfileDao(dbManager.getTablePrefix());
                List<PlayerProfileDao.TopActivePlayer> players = dao.queryTopActive(conn, afterTime, orderBy, limit);

                List<Map<String, Object>> formatted = formatTopActivePlayers(players);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("order_by", orderBy);
                data.put("players", formatted);
                data.put("count", players.size());

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("服务器活跃玩家排行（{}，按{}排序，Top {}）", timeRange, orderBy, limit));
                if (players.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("该时间段内服务器无活跃玩家"));
                } else {
                    int rank = 1;
                    for (Map<String, Object> row : formatted) {
                        msg.append("\n  ").append(rank++).append(". ").append(row.get("name")).append(" - ").append(I18nService.tr("登录")).append(" ").append(row.get("login_count")).append(I18nService.tr("次")).append(", ").append(I18nService.tr("在线")).append(" ").append(row.get("total_playtime_hours")).append("h");
                    }
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询活跃玩家失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询活跃玩家失败: {}", e.getMessage()));
            }
        }, LOG_PREFIX);
    }

    /**
     * 新玩家流入 — PLAYER_FIRST_JOIN 事件按时间聚合
     */
    private CompletableFuture<SkillResult> executeNewPlayers(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = SkillEntityHelper.getString(context, "time_range");
            String groupByStr = SkillEntityHelper.getString(context, "group_by");

            String timeRange = (timeRangeStr != null) ? timeRangeStr : "7d";
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

                List<Map<String, Object>> formatted = formatBucketCounts(counts);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("time_range", timeRange);
                data.put("group_by", groupBy);
                data.put("total_new_players", totalNew);
                data.put("buckets", formatted);

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("服务器新玩家流入统计（{}，共 {} 人）", timeRange, totalNew));
                if (!counts.isEmpty()) {
                    for (Map<String, Object> row : formatted) {
                        String dateStr = AdminSkillUtil.formatTimestamp((Long) row.get("time_bucket"));
                        msg.append("\n  ").append(dateStr).append(": ").append(row.get("count")).append(I18nService.tr("人"));
                    }
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询新玩家流入失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询新玩家流入失败: {}", e.getMessage()));
            }
        }, LOG_PREFIX);
    }

    /**
     * 画像分析覆盖率 — 已分析/待分析/覆盖率百分比
     */
    private CompletableFuture<SkillResult> executeProfileCoverage(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String timeRangeStr = SkillEntityHelper.getString(context, "time_range");
            String limitStr = SkillEntityHelper.getString(context, "limit");

            String timeRange = (timeRangeStr != null) ? timeRangeStr : "7d";
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
                double coveragePct = Math.round(coverage.coveragePct() * 10.0) / 10.0;
                data.put("coverage_pct", coveragePct);
                data.put("pending_analysis", formatTopActivePlayers(pending));

                // 使用 I18nService.tr 的 {} 占位符，避免 String.format 的 %% 转义问题
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("全服画像覆盖率: {}/{}（{}%），近期新分析 {} 人，待分析 {} 人", coverage.analyzedCount(), coverage.totalPlayers(), coveragePct, coverage.recentlyAnalyzed(), pending.size()));
                if (!pending.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("待分析玩家:"));
                    for (Map<String, Object> p : formatTopActivePlayers(pending)) {
                        msg.append("\n  - ").append(p.get("name")).append(" (").append(I18nService.tr("登录")).append(" ").append(p.get("login_count")).append(I18nService.tr("次")).append(")");
                    }
                }
                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询画像覆盖率失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询画像覆盖率失败: {}", e.getMessage()));
            }
        }, LOG_PREFIX);
    }

    /**
     * 社交图谱洞察 — 社交网络密度、孤立玩家检测、Top 关系
     */
    private CompletableFuture<SkillResult> executeSocialInsights(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String limitStr = SkillEntityHelper.getString(context, "limit");
            int limit = AdminSkillUtil.parseLimit(limitStr, 10);

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                SocialRelationDao socialDao = new SocialRelationDao(dbManager.getTablePrefix());

                List<SocialRelationDao.SocialStats> topConnected = socialDao.querySocialStats(conn, limit);
                int totalRelations = socialDao.countTotalRelations(conn);
                List<SocialRelationDao.IsolatedPlayer> isolated = socialDao.queryIsolatedPlayers(conn, 0, limit);

                // 计算平均关系强度
                double avgStrength = topConnected.stream().mapToDouble(SocialRelationDao.SocialStats::avgStrength).average().orElse(0.0);
                double roundedAvg = Math.round(avgStrength * 1000.0) / 1000.0;

                Map<String, Object> networkStats = new LinkedHashMap<>();
                networkStats.put("total_relations", totalRelations);
                networkStats.put("avg_strength", roundedAvg);
                networkStats.put("isolated_count", isolated.size());

                List<Map<String, Object>> formattedStats = formatSocialStats(topConnected);
                List<Map<String, Object>> formattedIsolated = formatIsolatedPlayers(isolated);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("network_stats", networkStats);
                data.put("top_connected", formattedStats);
                data.put("isolated_players", formattedIsolated);

                // 构建包含数据的 message，供 LLM 二次分析直接使用
                // 反查 UUID → 玩家名
                Map<String, String> nameCache = new HashMap<>();

                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("全服社交网络: {} 条关系，平均强度 {}，{} 名孤立玩家", totalRelations, roundedAvg, isolated.size()));
                if (!topConnected.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("社交最活跃:"));
                    int rank = 1;
                    for (SocialRelationDao.SocialStats s : topConnected) {
                        String pName = AdminSkillUtil.resolvePlayerName(s.playerUuid().toString(), conn, nameCache);
                        String strengthLevel = AdminSkillUtil.formatStrengthLevel(s.avgStrength());
                        msg.append("\n  ").append(rank++).append(". ").append(pName).append(" - ").append(I18nService.tr("关系")).append(" ").append(s.relationCount()).append(I18nService.tr("条")).append(", ").append(I18nService.tr("平均强度")).append(" ").append(strengthLevel);
                    }
                }
                if (!isolated.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("孤立玩家:"));
                    for (Map<String, Object> row : formattedIsolated) {
                        msg.append("\n  - ").append(row.get("name"));
                    }
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询社交图谱失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询社交图谱失败: {}", e.getMessage()));
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

    /**
     * 指定玩家社交关系查询 — 查询某个玩家的社交关系详情
     *
     * <p>管理员功能，查询指定玩家的社交关系列表（按关系强度降序）。</p>
     *
     * @since 2026-05-27
     */
    private CompletableFuture<SkillResult> executePlayerRelations(SkillContext context) {
        return AdminSkillUtil.executeAsync(() -> {
            String playerName = SkillEntityHelper.getString(context, "player_name");
            String limitStr = SkillEntityHelper.getString(context, "limit");
            int limit = AdminSkillUtil.parseLimit(limitStr, 20);

            if (playerName == null || playerName.isEmpty()) {
                return SkillResult.needInfo(I18nService.tr("请告诉我要查询哪位玩家的社交关系"));
            }

            DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
            try (Connection conn = dbManager.getConnection()) {
                // 1. 通过玩家名查 UUID
                PlayerProfileDao profileDao = new PlayerProfileDao(dbManager.getTablePrefix());
                var profile = profileDao.loadByName(conn, playerName);
                if (profile == null) {
                    return SkillResult.success(I18nService.tr("未找到玩家: {}", playerName));
                }

                UUID playerUuid = profile.getUuid();

                // 2. 查询社交关系
                SocialRelationDao socialDao = new SocialRelationDao(dbManager.getTablePrefix());
                List<SocialRelationDao.SocialRelation> relations = socialDao.loadRelations(conn, playerUuid, limit);

                // 3. 反查 target_uuid → name（在线缓存 + 库查询）
                Map<UUID, String> nameCache = new HashMap<>(SkillSecurityFilter.getOnlineUuidToName());

                List<Map<String, Object>> formatted = new ArrayList<>();
                for (SocialRelationDao.SocialRelation r : relations) {
                    String targetName = resolvePlayerName(r.targetUuid(), nameCache, conn, profileDao);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("target_name", targetName);
                    row.put("target_uuid", r.targetUuid().toString());
                    row.put("relation_type", r.relationType());
                    row.put("strength", Math.round(r.strength() * 1000.0) / 1000.0);
                    row.put("interaction_count", r.interactionCount());
                    row.put("last_interaction", formatTimestamp(r.lastInteraction()));
                    formatted.add(row);
                }

                // 4. 构建 data（数据契约与 YML 声明严格一致）
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("player_name", playerName);
                data.put("player_uuid", playerUuid.toString());
                data.put("relation_count", relations.size());
                data.put("relations", formatted);

                // 5. 构建 message（以被查询玩家为主语，自包含数据，强度等级化）
                StringBuilder msg = new StringBuilder();
                msg.append(I18nService.tr("玩家 {} 的社交关系（共 {} 条）", playerName, relations.size()));
                if (relations.isEmpty()) {
                    msg.append("\n").append(I18nService.tr("该玩家暂无社交关系记录"));
                } else {
                    for (SocialRelationDao.SocialRelation r : relations) {
                        String targetName = resolvePlayerName(r.targetUuid(), nameCache, conn, profileDao);
                        String strengthLevel = AdminSkillUtil.formatStrengthLevel(r.strength());
                        String lastTime = AdminSkillUtil.formatTimestamp(r.lastInteraction());
                        msg.append("\n  - ").append(targetName).append(" [").append(r.relationType()).append("]").append(" - ").append(I18nService.tr("强度")).append(" ").append(strengthLevel).append(", ").append(I18nService.tr("互动")).append(" ").append(r.interactionCount()).append(I18nService.tr("次")).append(", ").append(I18nService.tr("最近")).append(" ").append(lastTime);
                    }
                }

                return SkillResult.success(msg.toString(), data);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询玩家社交关系失败: {}", e.getMessage()), e);
                return SkillResult.failure(I18nService.tr("查询玩家社交关系失败: {}", e.getMessage()));
            }
        }, LOG_PREFIX);
    }

    /**
     * 反查 UUID → 玩家名（先查缓存，再查库）
     */
    private String resolvePlayerName(UUID uuid, Map<UUID, String> cache, Connection conn, PlayerProfileDao profileDao) throws SQLException {
        String cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        // 库查询
        var profile = profileDao.loadByUuid(conn, uuid);
        String name = profile != null ? profile.getName() : uuid.toString().substring(0, 8) + "...";
        cache.put(uuid, name); // 缓存避免重复查库
        return name;
    }

    /**
     * 格式化时间戳为可读字符串
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "-";
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
