package com.zm.kilacraftAI.service.event;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao.SocialRelation;
import com.zm.kilacraftAI.model.greeting.FriendStatus;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.profile.ProfileManager;
import com.zm.kilacraftAI.skills.framework.SkillSecurityFilter;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
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

    /**
     * 问候更新检测的实例级冷却（1 小时），避免管理员频繁登录反复发 HTTP 请求
     */
    private static final long UPDATE_CHECK_COOLDOWN_MS = 3600_000L;

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private volatile ServerEventDao serverEventDao;
    private volatile SocialRelationDao socialRelationDao;
    private volatile PlayerProfileDao playerProfileDao;

    public OfflineEventAggregator(KilacraftAI plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.serverEventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.socialRelationDao = new SocialRelationDao(databaseManager.getTablePrefix());
        this.playerProfileDao = new PlayerProfileDao(databaseManager.getTablePrefix());
    }

    /**
     * 热重载配置（重建所有 DAO 以反映最新的表前缀）
     */
    public void refreshConfig() {
        String prefix = databaseManager.getTablePrefix();
        this.serverEventDao = new ServerEventDao(prefix);
        this.socialRelationDao = new SocialRelationDao(prefix);
        this.playerProfileDao = new PlayerProfileDao(prefix);
    }

    /**
     * 聚合查询
     *
     * @param playerUuid          目标玩家 UUID
     * @param afterTime           上次登出时间戳（ms）
     * @param lastGreetingTime    上次问候时间戳（ms），0 表示从未问候过
     * @param maxOwnEvents        分类一最大条数
     * @param maxFriendEvents     分类二最大条数
     * @param maxSummaryEvents    分类三最大条数
     * @param loadHealthAlerts    是否查询健康告警（仅管理员）
     * @param loadUpdateReminders 是否检测并查询新版本提醒（仅管理员）
     * @param callback            完成回调（在 IO 线程执行）
     */
    public void loadAllOfflineDataForGreeting(UUID playerUuid, long afterTime, long lastGreetingTime, int maxOwnEvents, int maxFriendEvents, int maxSummaryEvents, boolean loadHealthAlerts, boolean loadUpdateReminders, Consumer<GreetingOfflineData> callback) {
        if (databaseManager == null) {
            callback.accept(GreetingOfflineData.empty());
            return;
        }

        FoliaCompat.getIOPool().submit(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 玩家自己的离线事件
                List<ServerEvent> ownEvents = serverEventDao.loadEventsAfter(conn, playerUuid, afterTime, maxOwnEvents);

                // 好友离线期间的动态
                // 先加载社交关系
                List<SocialRelation> relations = socialRelationDao.loadRelationsAbove(conn, playerUuid, FRIEND_STRENGTH_THRESHOLD, 50);
                List<UUID> friendUuids = new ArrayList<>();
                for (SocialRelation r : relations) {
                    friendUuids.add(r.targetUuid());
                }
                List<ServerEvent> friendEvents = friendUuids.isEmpty() ? Collections.emptyList() : serverEventDao.loadEventsForPlayers(conn, friendUuids, afterTime, maxFriendEvents);

                // 上次游玩亮点
                List<ServerEvent> highlights = serverEventDao.loadEventsBetween(conn, playerUuid, lastGreetingTime, afterTime, maxSummaryEvents);

                // 上次会话时长
                long lastSessionDurationMs = serverEventDao.loadLastSessionDuration(conn, playerUuid);

                // 离线期间全服热度
                int globalEventCount = serverEventDao.countGlobalEventsBetween(conn, afterTime, System.currentTimeMillis());

                // 好友离线期间登录次数
                Map<String, Integer> friendLoginCounts = serverEventDao.countFriendLogins(conn, friendUuids, afterTime);

                // 在线好友（带世界+会话时长） + 离线好友最后在线时间
                List<FriendStatus> onlineFriends = new ArrayList<>();
                List<FriendStatus> offlineFriends = new ArrayList<>();
                loadAllFriendsWithStatus(conn, relations, onlineFriends, offlineFriends);

                // 系统健康告警事件（仅管理员需要：查离线期间尚未被问候通知过的告警）
                List<ServerEvent> healthAlerts;
                if (loadHealthAlerts) {
                    healthAlerts = serverEventDao.loadUnnotifiedHealthAlerts(conn, playerUuid, afterTime, 50);
                    // 标记已通知，保证每条告警每管理员只提醒一次
                    for (ServerEvent alert : healthAlerts) {
                        serverEventDao.markHealthAlertNotified(conn, playerUuid, alert.getData(), "");
                    }
                } else {
                    healthAlerts = Collections.emptyList();
                }

                // 新版本可用提醒（仅管理员需要：先做带冷却的检测写库，再查该管理员尚未被通知的版本）
                List<ServerEvent> updateReminders;
                if (loadUpdateReminders && plugin.getUpdateChecker() != null) {
                    plugin.getUpdateChecker().checkAndPersistIfNeeded(conn, serverEventDao, "", UPDATE_CHECK_COOLDOWN_MS);
                    updateReminders = serverEventDao.loadUnnotifiedUpdateReminders(conn, playerUuid, 5);
                    // 查到即标记该管理员已通知，保证每版本每管理员只提醒一次
                    for (ServerEvent reminder : updateReminders) {
                        serverEventDao.markUpdateNotified(conn, playerUuid, reminder.getData(), "");
                    }
                } else {
                    updateReminders = Collections.emptyList();
                }

                callback.accept(new GreetingOfflineData(ownEvents, friendEvents, highlights, onlineFriends, offlineFriends, lastSessionDurationMs, globalEventCount, friendLoginCounts, healthAlerts, updateReminders));
            } catch (SQLException e) {
                PluginLoggerUtil.warn("数据库", "离线数据聚合失败: {}", e.getMessage());
                callback.accept(GreetingOfflineData.empty());
            }
        });
    }

    /**
     * 加载所有好友（在线+离线）
     *
     * <p>复用已加载的社交关系列表，避免重复查询 social_relation 表。</p>
     * <p>在线好友：构建 FriendStatus（含世界名+会话时长）</p>
     * <p>离线好友：批量查询 last_logout 构建带时间差的 FriendStatus</p>
     */
    private void loadAllFriendsWithStatus(Connection conn, List<SocialRelation> relations, List<FriendStatus> outOnlineFriends, List<FriendStatus> outOfflineFriends) throws SQLException {
        if (relations.isEmpty()) return;
        Map<UUID, String> onlineUuidToName = SkillSecurityFilter.getOnlineUuidToName();

        List<UUID> offlineUuids = new ArrayList<>();

        for (SocialRelation relation : relations) {
            UUID targetUuid = relation.targetUuid();
            String onlineName = onlineUuidToName.get(targetUuid);
            if (onlineName != null) {
                // 在线好友
                String world = getFriendWorld(targetUuid);
                long sessionMinutes = getFriendSessionMinutes(targetUuid);
                outOnlineFriends.add(new FriendStatus(onlineName, world, sessionMinutes));
            } else {
                // 离线好友
                offlineUuids.add(targetUuid);
            }
        }

        if (!offlineUuids.isEmpty()) {
            List<PlayerProfileDao.OfflineFriendData> offlineData = playerProfileDao.batchLoadLastLogout(conn, offlineUuids);
            long now = System.currentTimeMillis();
            for (PlayerProfileDao.OfflineFriendData data : offlineData) {
                long minutesAgo = TimeUnit.MILLISECONDS.toMinutes(now - data.lastLogout());
                if (minutesAgo > 0) {
                    outOfflineFriends.add(new FriendStatus(data.name(), "", minutesAgo));
                }
            }
        }
    }

    /**
     * 获取好友当前世界名
     */
    private String getFriendWorld(UUID uuid) {
        var player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player == null) return "";
        return player.getWorld().getName();
    }

    /**
     * 获取好友当前会话时长（分钟）
     */
    private long getFriendSessionMinutes(UUID uuid) {
        ProfileManager pm = plugin.getProfileManager();
        if (pm == null) return 0;
        PlayerProfile profile = pm.getCachedProfile(uuid);
        if (profile == null || profile.getLastLogin() <= 0) return 0;
        long sessionMs = System.currentTimeMillis() - profile.getLastLogin();
        return TimeUnit.MILLISECONDS.toMinutes(sessionMs);
    }

    /**
     * 问候离线数据聚合结果
     */
    public record GreetingOfflineData(List<ServerEvent> ownEvents, List<ServerEvent> friendEvents,
                                      List<ServerEvent> highlights, List<FriendStatus> onlineFriends,
                                      List<FriendStatus> offlineFriends, long lastSessionDurationMs,
                                      int globalEventCount, Map<String, Integer> friendLoginCounts,
                                      List<ServerEvent> healthAlerts, List<ServerEvent> updateReminders) {
        public static GreetingOfflineData empty() {
            return new GreetingOfflineData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, 0, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
