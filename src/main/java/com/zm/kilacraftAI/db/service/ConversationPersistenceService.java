package com.zm.kilacraftAI.db.service;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ConversationDao;
import com.zm.kilacraftAI.db.dao.WatermarkDao;
import com.zm.kilacraftAI.db.model.DatabaseConfig;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 对话持久化中间层
 *
 * <p>职责：</p>
 * <ul>
 *     <li>接收内存中的新消息，提交到异步写入队列（Write-Behind）</li>
 *     <li>定时批量写入 DB（每 30 秒或队列 >= 20 条触发）</li>
 *     <li>按需从 DB 加载历史到内存（Lazy Loading）</li>
 *     <li>过期数据清理（每 6 小时执行）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public class ConversationPersistenceService {

    /**
     * 批量写入的最大条数
     */
    private static final int BATCH_SIZE = 50;
    /**
     * 触发立即刷盘的队列阈值
     */
    private static final int FLUSH_THRESHOLD = 20;
    /**
     * 过期清理单次删除上限
     */
    private static final int CLEANUP_BATCH_SIZE = 10000;

    /**
     * 清理周期水位名称（分布式锁标识）
     *
     * <p>清理任务不分 server_id，由分布式锁保证全局只执行一次，替所有子服清理过期数据。
     * 这与 social_relation 的水位策略不同——社交衰减按共享策略决定是否带后缀。</p>
     */
    private static final String CLEANUP_WATERMARK_NAME = "cleanup_conversation";

    /**
     * 清理周期时长（6 小时 = 21600000ms）
     */
    private static final long CLEANUP_INTERVAL_MS = 21600000L;

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private volatile ConversationDao conversationDao;
    private volatile WatermarkDao watermarkDao;
    private final ConversationManager conversationManager;
    /**
     * 最大历史轮数（内存策略，非 DB 配置）。volatile 支持 reload 刷新。
     */
    private volatile int maxHistory;
    /**
     * 保留天数（0=永久保留）
     */
    @Getter
    private volatile int retentionDays;
    private volatile boolean loadHistoryEnabled;
    /**
     * 当前服务器标识（群组服区分）
     */
    private volatile String serverId;

    /**
     * 待写入消息队列
     */
    private final ConcurrentLinkedQueue<PendingMessage> writeQueue = new ConcurrentLinkedQueue<>();

    /**
     * 创建对话持久化服务
     *
     * @param plugin              插件实例
     * @param databaseManager     数据库管理器
     * @param conversationManager 对话管理器
     * @param maxHistory          最大历史轮数
     * @param retentionDays       对话保留天数（0=永久）
     * @param loadHistoryEnabled  是否启用历史加载
     */
    public ConversationPersistenceService(KilacraftAI plugin, DatabaseManager databaseManager, ConversationManager conversationManager, int maxHistory, int retentionDays, boolean loadHistoryEnabled, String serverId) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.conversationDao = new ConversationDao(databaseManager.getTablePrefix());
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
        this.conversationManager = conversationManager;
        this.maxHistory = maxHistory;
        this.retentionDays = retentionDays;
        this.loadHistoryEnabled = loadHistoryEnabled;
        this.serverId = serverId != null ? serverId : "";
    }

    /**
     * 提交一条消息到异步写入队列
     *
     * @param playerUuid  玩家 UUID
     * @param role        角色（"user" / "assistant"）
     * @param content     消息内容
     * @param personality 人格标识（普通AI为空串）
     * @param source      来源标识（"chat" / "command" / "plugin" / "greeting"）
     */
    public void submit(UUID playerUuid, String role, String content, String personality, String source) {
        if (playerUuid == null || content == null) return;

        writeQueue.add(new PendingMessage(playerUuid, role, content, personality, source));

        // 队列达到阈值，异步触发刷盘
        if (writeQueue.size() >= FLUSH_THRESHOLD) {
            FoliaCompat.getIOPool().submit(this::doFlush);
        }
    }

    /**
     * 刷盘指定玩家的待写入消息（玩家退出时调用）
     *
     * <p>使用 poll+过滤+放回模式，避免与 {@link #doFlush()} 的并发冲突。
     * ConcurrentLinkedQueue 的 iterator+remove 在弱一致性下可能与 poll 产生竞态，
     * 导致同一条消息被两方各取一次、重复写入 DB。</p>
     *
     * @param playerUuid 玩家 UUID
     */
    public void flushPlayer(UUID playerUuid) {
        List<PendingMessage> playerMessages = new ArrayList<>();
        List<PendingMessage> others = new ArrayList<>();

        // 一次性排空队列，按玩家分组
        PendingMessage msg;
        while ((msg = writeQueue.poll()) != null) {
            if (msg.playerUuid.equals(playerUuid)) {
                playerMessages.add(msg);
            } else {
                others.add(msg);
            }
        }

        // 将非该玩家的消息放回队列
        if (!others.isEmpty()) {
            writeQueue.addAll(others);
        }

        if (!playerMessages.isEmpty()) {
            FoliaCompat.getIOPool().submit(() -> {
                writeBatch(playerMessages);
                PluginLoggerUtil.debug("数据库", "已刷盘玩家 {} 的 {} 条待写入消息", playerUuid, playerMessages.size());
            });
        }
    }

    /**
     * 刷盘所有待写入消息（插件关闭时调用，同步）
     */
    public void flushAll() {
        List<PendingMessage> all = new ArrayList<>();
        while (!writeQueue.isEmpty()) {
            PendingMessage msg = writeQueue.poll();
            if (msg != null) all.add(msg);
        }

        if (!all.isEmpty()) {
            try {
                writeBatch(all);
                PluginLoggerUtil.info("数据库", "已刷盘全部 {} 条待写入消息", all.size());
            } catch (Exception e) {
                PluginLoggerUtil.error("数据库", "关闭时刷盘失败，丢失 {} 条消息: {}", all.size(), e.getMessage());
            }
        }
    }

    /**
     * 定时刷盘任务（由 TaskScheduler 调度）
     *
     * @return 实际刷盘的消息条数
     */
    public int scheduledFlush() {
        if (writeQueue.isEmpty()) return 0;
        return doFlush();
    }

    /**
     * 执行批量写入
     *
     * @return 实际写入的消息条数
     */
    private int doFlush() {
        List<PendingMessage> batch = new ArrayList<>(BATCH_SIZE);
        while (!writeQueue.isEmpty() && batch.size() < BATCH_SIZE) {
            PendingMessage msg = writeQueue.poll();
            if (msg != null) batch.add(msg);
        }

        if (batch.isEmpty()) return 0;

        // 失败重试 1 次
        try {
            writeBatch(batch);
        } catch (Exception e) {
            PluginLoggerUtil.warn("数据库", "批量写入失败，重试一次: {}", e.getMessage());
            try {
                writeBatch(batch);
            } catch (Exception retryEx) {
                PluginLoggerUtil.warn("数据库", "重试写入仍失败，丢弃 {} 条消息: {}", batch.size(), retryEx.getMessage());
            }
        }
        return batch.size();
    }

    /**
     * 将消息列表写入数据库
     */
    private void writeBatch(List<PendingMessage> messages) {
        List<String[]> rows = new ArrayList<>(messages.size());
        for (PendingMessage msg : messages) {
            rows.add(new String[]{msg.playerUuid.toString(), msg.role, msg.content, msg.personality != null ? msg.personality : "", msg.source, String.valueOf(msg.createdAt)});
        }

        try (Connection conn = databaseManager.getConnection()) {
            conversationDao.batchInsert(conn, rows, serverId);
        } catch (SQLException e) {
            throw new RuntimeException(I18nService.tr("写入对话记录失败"), e);
        }
    }

    /**
     * 异步加载历史记录到内存，加载完成后回调
     *
     * <p>如果内存已有数据则立即回调（跳过 DB 查询）。</p>
     * <p>如果数据库不可用或未启用历史加载，使用空历史。</p>
     *
     * @param playerUuid  玩家 UUID
     * @param personality 人格标识（普通AI为空串）
     * @param sources     要加载的来源枚举（内部自动构建 SQL IN 子句）
     * @param callback    加载完成回调（在 IO 线程执行）
     */
    public void loadHistoryIfNeeded(UUID playerUuid, String personality, Consumer<Deque<ConversationManager.Message>> callback, ConversationSourceEnum... sources) {
        if (!loadHistoryEnabled) {
            // 未启用历史加载，直接使用内存历史
            Deque<ConversationManager.Message> existing = getExistingHistory(playerUuid, personality);
            callback.accept(existing != null ? existing : new ArrayDeque<>());
            return;
        }

        // 检查内存是否已有（去重保护）
        // 注意：仅包含 assistant 消息的历史（如登录问候）不视为有效历史，仍需从 DB 加载
        Deque<ConversationManager.Message> existing = getExistingHistory(playerUuid, personality);
        if (existing != null && !existing.isEmpty() && existing.stream().anyMatch(m -> !"assistant".equals(m.getRole()))) {
            callback.accept(existing);
            return;
        }

        // 检查 cleared 标记：玩家执行过 /kila clear，跳过 DB 加载，从空白上下文开始
        if (conversationManager.consumeCleared(playerUuid)) {
            callback.accept(new ArrayDeque<>());
            return;
        }

        // 从枚举构建 SQL IN 子句
        String sourceFilter = buildSourceFilter(sources);

        // 异步从 DB 加载
        FoliaCompat.getIOPool().submit(() -> {
            try {
                Deque<ConversationManager.Message> loaded = loadFromDB(playerUuid, personality, sourceFilter);
                callback.accept(loaded);
            } catch (Exception e) {
                PluginLoggerUtil.warn("数据库", "加载历史记录失败，使用空历史: {}", e.getMessage());
                callback.accept(new ArrayDeque<>());
            }
        });
    }

    /**
     * 将 DB 加载的历史合并到内存历史中
     *
     * <p>DB 历史在前（时间升序），内存中可能存在的 assistant-only 消息（如登录问候）保留在末尾。
     * 当 loadHistoryIfNeeded 判定 assistant-only 的内存历史为“空”并从 DB 加载后，
     * 调用此方法完成合并。</p>
     *
     * @param loadedHistory 从 DB 加载的历史（可能为空）
     * @param playerHistory 内存中的历史队列（可能包含问候消息）
     */
    public static void mergeLoadedHistory(Deque<ConversationManager.Message> loadedHistory, Deque<ConversationManager.Message> playerHistory) {
        // 自引用保护：如果 loadedHistory 就是 playerHistory 本身（内存历史已有效时会出现），无需合并
        if (loadedHistory == null || loadedHistory.isEmpty() || playerHistory == null || loadedHistory == playerHistory) {
            return;
        }
        ConversationManager.Message last = playerHistory.peekLast();
        playerHistory.clear();
        playerHistory.addAll(loadedHistory);
        if (last != null) {
            playerHistory.addLast(last);
        }
    }

    /**
     * 从内存中获取已有历史
     */
    private Deque<ConversationManager.Message> getExistingHistory(UUID playerUuid, String personality) {
        if (personality == null || personality.isEmpty()) {
            return conversationManager.getHistory(playerUuid);
        } else {
            String key = playerUuid.toString() + "_" + personality;
            return conversationManager.getPluginHistory(key);
        }
    }

    /**
     * 从数据库加载历史记录
     */
    private Deque<ConversationManager.Message> loadFromDB(UUID playerUuid, String personality, String sourceFilter) throws SQLException {
        // max_history=0（禁用历史）时不加载任何旧记录，与 saveToHistory 遇 0 不保存构成完整禁用语义
        if (maxHistory <= 0) {
            return new ArrayDeque<>();
        }
        // DB 加载量 = maxHistory 条 = maxHistory/2 轮 = 内存容量（maxHistory*2 条）的一半。
        int limit = maxHistory;
        try (Connection conn = databaseManager.getConnection()) {
            return conversationDao.loadHistory(conn, playerUuid.toString(), personality != null ? personality : "", sourceFilter, limit);
        }
    }

    /**
     * 从枚举数组构建 SQL IN 子句
     *
     * <p>例：{@code buildSourceFilter(CHAT, COMMAND)} → {@code "'chat','command'"}</p>
     *
     * @param sources 来源枚举数组
     * @return SQL IN 子句值
     */
    private static String buildSourceFilter(ConversationSourceEnum... sources) {
        return Arrays.stream(sources).map(s -> "'" + s.getValue() + "'").collect(Collectors.joining(","));
    }

    /**
     * 定时过期清理任务（由 TaskScheduler 调度）
     *
     * <p>分布式安全：使用 watermark 行锁（SELECT FOR UPDATE）保证群组服中只有一个子服执行清理。</p>
     *
     * @return 实际清理的记录条数
     */
    public int scheduledCleanup() {
        if (retentionDays <= 0) return 0;

        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

        try (var conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 分布式锁：锁定水位行
                String lastCleanupStr = watermarkDao.getForUpdate(conn, CLEANUP_WATERMARK_NAME);
                long now = System.currentTimeMillis();

                // 检查是否在当前周期内已清理
                if (lastCleanupStr != null) {
                    try {
                        long lastTime = Long.parseLong(lastCleanupStr);
                        if (now - lastTime < CLEANUP_INTERVAL_MS) {
                            conn.rollback();
                            return 0; // 其他子服已清理
                        }
                    } catch (NumberFormatException ignored) {
                        // 水位值异常，视为需要重新清理
                    }
                }

                // 执行清理
                int totalDeleted = 0;
                int deleted;
                do {
                    deleted = conversationDao.cleanExpired(conn, cutoffTime, CLEANUP_BATCH_SIZE);
                    totalDeleted += deleted;
                } while (deleted >= CLEANUP_BATCH_SIZE);

                // 原子提交：清理 + 水位
                watermarkDao.put(conn, CLEANUP_WATERMARK_NAME, String.valueOf(now));
                conn.commit();

                if (totalDeleted > 0) {
                    PluginLoggerUtil.info("数据库", "已清理 {} 条过期对话记录（保留天数: {}）", totalDeleted, retentionDays);
                }
                return totalDeleted;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            PluginLoggerUtil.warn("数据库", "过期清理失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 热重载配置（由 /kila reload 触发）
     *
     * <p>更新可热重载的配置项：历史加载开关、保留天数、表前缀（DAO 重建）、server_id。</p>
     *
     * @param config 新的数据库配置
     */
    public void refreshConfig(DatabaseConfig config) {
        this.loadHistoryEnabled = config.isLoadHistoryOnLogin();
        this.retentionDays = config.getConversationRetentionDays();
        this.serverId = config.getServerId() != null ? config.getServerId() : "";
        String prefix = databaseManager.getTablePrefix();
        this.conversationDao = new ConversationDao(prefix);
        this.watermarkDao = new WatermarkDao(prefix);
        PluginLoggerUtil.info("数据库", "对话持久化服务配置已刷新（历史加载: {}, 保留天数: {}, server_id: {}）", loadHistoryEnabled, retentionDays > 0 ? retentionDays : I18nService.tr("永久"), serverId.isEmpty() ? I18nService.tr("未配置") : serverId);
    }

    /**
     * 刷新 max_history（内存策略，非 DB 配置）。由 /kila reload 在读取主配置后调用，
     * 使 {@code settings.max_history} 改动即时影响 DB 历史加载量。
     * <p>钳制到 0-100，与 {@code ConversationManager.setMaxHistoryRounds} 语义一致。</p>
     */
    public void refreshMaxHistory(int maxHistory) {
        this.maxHistory = Math.max(0, Math.min(100, maxHistory));
    }

    /**
     * 关闭服务：刷盘所有待写入消息
     *
     * <p>定时任务的取消由 TaskScheduler.shutdownAll() 统一处理。</p>
     */
    public void shutdown() {
        // 同步刷盘所有剩余消息
        flushAll();
        PluginLoggerUtil.info("数据库", "对话持久化服务已关闭");
    }

    /**
     * 待写入消息
     */
    public static class PendingMessage {
        private final UUID playerUuid;
        private final String role;
        private final String content;
        private final String personality;
        private final String source;
        private final long createdAt;

        public PendingMessage(UUID playerUuid, String role, String content, String personality, String source) {
            this.playerUuid = playerUuid;
            this.role = role;
            this.content = content;
            this.personality = personality;
            this.source = source;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
