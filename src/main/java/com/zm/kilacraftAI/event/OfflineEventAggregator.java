package com.zm.kilacraftAI.event;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao.SocialRelation;
import com.zm.kilacraftAI.skills.framework.SkillSecurityFilter;
import com.zm.kilacraftAI.util.PluginLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/**
 * 离线事件聚合器
 *
 * @author Zm_Mmm
 */
public class OfflineEventAggregator {

    /**
     * 好友推荐的最低关系强度阈值
     */
    private static final double FRIEND_STRENGTH_THRESHOLD = 0.1;

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private final ServerEventDao serverEventDao;
    private final SocialRelationDao socialRelationDao;

    public OfflineEventAggregator(KilacraftAI plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.serverEventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.socialRelationDao = new SocialRelationDao(databaseManager.getTablePrefix());
    }

    /**
     * 三路查询合并为单次 IO 任务，共享一个数据库连接
     *
     * <p>在单个 IO 线程内顺序执行三路查询 + 在线好友查询，
     * 通过单个回调返回所有结果，避免连接池浪费和嵌套回调开销。</p>
     *
     * @param playerUuid       目标玩家 UUID
     * @param playerName       目标玩家名
     * @param afterTime        上次登出时间戳（ms）
     * @param lastGreetingTime 上次问候时间戳（ms），0 表示从未问候过
     * @param maxOwnEvents     分类一最大条数
     * @param maxFriendEvents  分类二最大条数
     * @param maxSummaryEvents 分类三最大条数
     * @param callback         完成回调（在 IO 线程执行）
     */
    public void loadAllOfflineDataForGreeting(UUID playerUuid, String playerName, long afterTime, long lastGreetingTime, int maxOwnEvents, int maxFriendEvents, int maxSummaryEvents, Consumer<GreetingOfflineData> callback) {
        if (databaseManager == null) {
            callback.accept(GreetingOfflineData.empty());
            return;
        }

        FoliaCompat.getIOPool().submit(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 分类一：玩家自己的离线事件
                List<ServerEvent> ownEvents = serverEventDao.loadEventsAfter(conn, playerUuid, afterTime, maxOwnEvents);

                // 分类二：好友离线期间的动态
                List<ServerEvent> friendEvents = loadFriendEventsWithConn(conn, playerUuid, afterTime, maxFriendEvents);

                // 分类三：上次游玩亮点（仅取上次问候之后到本次登出之间的事件）
                List<ServerEvent> highlights = serverEventDao.loadEventsBetween(conn, playerUuid, lastGreetingTime, afterTime, maxSummaryEvents);

                // 在线好友（复用同一连接）
                List<String> onlineFriends = getOnlineFriendsWithConn(conn, playerUuid);

                callback.accept(new GreetingOfflineData(ownEvents, friendEvents, highlights, onlineFriends));
            } catch (SQLException e) {
                PluginLogger.warn("数据库", "离线数据聚合失败: {}", e.getMessage());
                callback.accept(GreetingOfflineData.empty());
            }
        });
    }

    /**
     * 使用已有连接加载好友事件（不独立获取连接）
     */
    private List<ServerEvent> loadFriendEventsWithConn(Connection conn, UUID playerUuid, long afterTime, int maxEvents) throws SQLException {
        List<SocialRelation> relations = socialRelationDao.loadRelationsAbove(conn, playerUuid, FRIEND_STRENGTH_THRESHOLD, 50);
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> friendUuids = new ArrayList<>();
        for (SocialRelation r : relations) {
            friendUuids.add(r.targetUuid());
        }
        return serverEventDao.loadEventsForPlayers(conn, friendUuids, afterTime, maxEvents);
    }

    /**
     * 使用已有连接获取在线好友（不独立获取连接）
     */
    private List<String> getOnlineFriendsWithConn(Connection conn, UUID playerUuid) throws SQLException {
        Map<UUID, String> onlineUuidToName = SkillSecurityFilter.getOnlineUuidToName();
        if (onlineUuidToName.isEmpty()) {
            return Collections.emptyList();
        }

        List<SocialRelation> relations = socialRelationDao.loadRelationsAbove(conn, playerUuid, FRIEND_STRENGTH_THRESHOLD, 50);
        List<String> friends = new ArrayList<>();
        for (SocialRelation relation : relations) {
            UUID targetUuid = relation.targetUuid();
            String targetName = onlineUuidToName.get(targetUuid);
            if (targetName != null) {
                friends.add(targetName);
            }
        }
        return friends;
    }

    /**
     * 问候离线数据聚合结果
     */
    public record GreetingOfflineData(List<ServerEvent> ownEvents, List<ServerEvent> friendEvents,
                                      List<ServerEvent> highlights, List<String> onlineFriends) {
        public static GreetingOfflineData empty() {
            return new GreetingOfflineData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
