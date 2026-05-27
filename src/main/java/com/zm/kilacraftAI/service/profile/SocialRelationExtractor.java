package com.zm.kilacraftAI.service.profile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.WatermarkDao;
import com.zm.kilacraftAI.model.profile.SocialGraph;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 社交关系智能提取器
 *
 * <p>定时从 kca_skill_log 表提取玩家交互信息，
 * 通过白名单预过滤 + 实体值扫描识别玩家名。</p>
 *
 * <p>这是社交关系系统的核心数据通道之一，弥补私聊监听无法覆盖的场景
 * （如 TPA 传送、市场转账等通过 AI Skill 触发的交互）。</p>
 */
public class SocialRelationExtractor {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    private final DatabaseManager databaseManager;
    private final SocialGraph socialGraph;
    private volatile WatermarkDao watermarkDao;

    /**
     * 当前服务器标识（群组服区分，影响水位名称后缀）
     */
    private volatile String serverId;

    /**
     * 上次提取时间（水位标记，避免重复处理）
     *
     * <p>内存缓存，用于快速跳过。分布式安全：实际查询前会从 DB 读取水位标记，
     * 确保群组服多子服场景下不会重复处理同一批 skill_log。</p>
     */
    private volatile long lastExtractTime = 0;

    public SocialRelationExtractor(DatabaseManager databaseManager, SocialGraph socialGraph, String serverId) {
        this.databaseManager = databaseManager;
        this.socialGraph = socialGraph;
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
        this.serverId = serverId != null ? serverId : "";
    }

    /**
     * 热重载 server_id 配置
     *
     * @param serverId 新的 server_id 值
     */
    public void refreshConfig(String serverId) {
        this.serverId = serverId != null ? serverId : "";
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
    }

    /**
     * 提取新的社交关系（分布式安全）
     *
     * <p>使用 FOR UPDATE 行锁 + 显式事务保证群组服中只有一个子服处理同一批数据。</p>
     * <p>水位名称带 server_id 后缀，实现各子服独立提取。</p>
     *
     * @return 本轮处理的 Skill 日志条数（0 表示无新数据）
     */
    public int extractNewRelations() {
        String watermarkName = buildWatermarkName("extract_time");

        try (var conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 分布式锁：锁定水位行
                String dbWatermarkStr = watermarkDao.getForUpdate(conn, watermarkName);
                long dbWatermark = 0;
                if (dbWatermarkStr != null) {
                    try {
                        dbWatermark = Long.parseLong(dbWatermarkStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (dbWatermark > lastExtractTime) {
                    lastExtractTime = dbWatermark;
                }

                long since = lastExtractTime > 0 ? lastExtractTime : System.currentTimeMillis() - 30 * 60 * 1000;
                List<SkillLogEntry> logs = loadRecentSkillLogs(conn, since);

                if (!logs.isEmpty()) {
                    Map<String, UUID> nameToUuid = loadPlayerNameMapping(conn);
                    for (SkillLogEntry log : logs) {
                        processSkillLog(log, nameToUuid);
                    }

                    long now = System.currentTimeMillis();
                    watermarkDao.put(conn, watermarkName, String.valueOf(now));
                    lastExtractTime = now;
                }

                conn.commit();
                return logs.size();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            PluginLoggerUtil.error("数据库", "社交关系提取失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 构建带 server_id 后缀的水位名称
     *
     * <p>server_id 为空时返回原始名称（单机服模式），否则返回 "name:server_id"。</p>
     */
    private String buildWatermarkName(String baseName) {
        return serverId.isEmpty() ? baseName : baseName + ":" + serverId;
    }

    /**
     * 构建当前配置的 Skill 白名单 SQL 片段（每次查询时动态读取，支持热重载）
     *
     * @return SQL IN 子句内容，如 {@code 'market_action','cmi','AFKTask'}
     */
    private String buildSkillFilter() {
        ConfigManager configManager = KilacraftAI.getInstance().getConfigManager();
        List<String> whitelist = configManager.getSocialSkillWhitelist();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < whitelist.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\'').append(whitelist.get(i).replace("'", "")).append('\'');
        }
        return sb.toString();
    }

    private List<SkillLogEntry> loadRecentSkillLogs(Connection conn, long since) throws SQLException {
        String sql = "SELECT player_uuid, skill_name, entities FROM " + databaseManager.getTablePrefix() + "skill_log " + "WHERE skill_name IN (" + buildSkillFilter() + ") " + "AND created_at >= ? AND success = true";

        List<SkillLogEntry> logs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new SkillLogEntry(rs.getString("player_uuid"), rs.getString("skill_name"), rs.getString("entities")));
                }
            }
        }
        return logs;
    }

    private Map<String, UUID> loadPlayerNameMapping(Connection conn) throws SQLException {
        String sql = "SELECT uuid, name FROM " + databaseManager.getTablePrefix() + "player_profile WHERE name IS NOT NULL";
        Map<String, UUID> mapping = new HashMap<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null) {
                    mapping.put(name.toLowerCase(), UUID.fromString(rs.getString("uuid")));
                }
            }
        }
        return mapping;
    }

    private void processSkillLog(SkillLogEntry log, Map<String, UUID> nameToUuid) {
        if (log.entitiesJson == null || log.entitiesJson.isEmpty()) return;

        Map<String, String> entities;
        try {
            entities = GSON.fromJson(log.entitiesJson, MAP_TYPE);
        } catch (Exception e) {
            return;
        }
        if (entities == null) return;

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(log.playerUuid);
        } catch (Exception e) {
            return;
        }

        Set<UUID> targetUuids = new HashSet<>();

        // 扫描所有 value，匹配玩家名
        for (String value : entities.values()) {
            if (value == null || value.isEmpty()) continue;
            if (!PLAYER_NAME_PATTERN.matcher(value).matches()) continue;

            UUID targetUuid = nameToUuid.get(value.toLowerCase());
            if (targetUuid != null && !targetUuid.equals(playerUuid)) {
                targetUuids.add(targetUuid);
            }
        }

        // 记录社交关系（双向，使用 SKILL_INTERACTION 权重）
        for (UUID targetUuid : targetUuids) {
            socialGraph.recordInteraction(playerUuid, targetUuid, "skill_interaction");
        }
    }

    /**
     * 技能日志条目（内部数据结构）
     */
    private record SkillLogEntry(String playerUuid, String skillName, String entitiesJson) {
    }
}
