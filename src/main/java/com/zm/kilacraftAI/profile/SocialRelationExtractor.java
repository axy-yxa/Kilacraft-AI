package com.zm.kilacraftAI.profile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.WatermarkDao;
import com.zm.kilacraftAI.util.PluginLogger;

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
    private final WatermarkDao watermarkDao;

    /**
     * 上次提取时间（水位标记，避免重复处理）
     *
     * <p>内存缓存，用于快速跳过。分布式安全：实际查询前会从 DB 读取水位标记，
     * 确保群组服多子服场景下不会重复处理同一批 skill_log。</p>
     */
    private volatile long lastExtractTime = 0;

    public SocialRelationExtractor(DatabaseManager databaseManager, SocialGraph socialGraph) {
        this.databaseManager = databaseManager;
        this.socialGraph = socialGraph;
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
    }

    /**
     * 提取新的社交关系
     *
     * @return 本轮处理的 Skill 日志条数（0 表示无新数据）
     */
    public int extractNewRelations() {
        // 直接在 TaskScheduler 的异步线程上执行（runAsyncTimer 回调已在异步线程）
        try (var conn = databaseManager.getConnection()) {
            // 分布式水位：从 DB 读取上次提取时间
            String dbWatermarkStr = watermarkDao.get(conn, "extract_time");
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

            // 1. 查询最近一个提取周期内白名单 Skill 的执行记录
            long since = lastExtractTime > 0 ? lastExtractTime : System.currentTimeMillis() - 30 * 60 * 1000;
            List<SkillLogEntry> logs = loadRecentSkillLogs(conn, since);

            if (!logs.isEmpty()) {
                // 2. 加载玩家名→UUID 映射（全量加载）
                Map<String, UUID> nameToUuid = loadPlayerNameMapping(conn);

                // 3. 扫描 entities 提取玩家交互
                for (SkillLogEntry log : logs) {
                    processSkillLog(log, nameToUuid);
                }
            }

            // 处理完成后再推进水位（确保失败时水位不前进，下次重新处理）
            // 仅在有数据时推进，避免因白名单配置错误导致水位越过后永远丢失历史记录
            if (!logs.isEmpty()) {
                long now = System.currentTimeMillis();
                watermarkDao.put(conn, "extract_time", String.valueOf(now));
                lastExtractTime = now;
            }
            return logs.size();
        } catch (Exception e) {
            PluginLogger.error("数据库", "社交关系提取失败: {}", e.getMessage());
            return 0;
        }
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
