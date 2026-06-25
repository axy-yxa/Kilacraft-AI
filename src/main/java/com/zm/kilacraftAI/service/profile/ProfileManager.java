package com.zm.kilacraftAI.service.profile;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.db.dao.ProfileSnapshotDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.event.EventCollector;
import lombok.Setter;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 玩家画像管理器
 *
 * <p>管理玩家画像的内存缓存、DB 读写、版本保护清理。</p>
 * <ul>
 *   <li>登录时异步从 DB 加载画像到缓存</li>
 *   <li>登出时立即异步写回 DB（不走 Write-Behind）</li>
 *   <li>延迟5分钟清理离线缓存，带版本戳保护防竞态</li>
 *   <li>缓存未命中时支持异步 DB 加载 + 回填</li>
 * </ul>
 *
 * @author Zm_Mmm
 */
public class ProfileManager {

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private PlayerProfileDao profileDao;
    private ProfileSnapshotDao snapshotDao;

    /**
     * 事件采集器（volatile 保证异步线程可见性：setEventCollector 在主线程调用，onPlayerJoin 回调在 IO 线程读取）
     */
    @Setter
    private volatile EventCollector eventCollector;

    public ProfileManager(KilacraftAI plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.profileDao = new PlayerProfileDao(databaseManager.getTablePrefix());
        this.snapshotDao = new ProfileSnapshotDao(databaseManager.getTablePrefix());
    }

    /**
     * 热重载配置（由 /kila reload 触发）
     *
     * <p>重建 DAO 以反映最新的表前缀。</p>
     * <p>其他配置（保留天数、分析间隔等）均通过
     * {@code databaseManager.getConfig()} 动态读取，无需额外刷新。</p>
     */
    public void refreshConfig() {
        String tablePrefix = databaseManager.getTablePrefix();
        this.profileDao = new PlayerProfileDao(tablePrefix);
        this.snapshotDao = new ProfileSnapshotDao(tablePrefix);
        PluginLoggerUtil.info("数据库", "画像管理器配置已刷新");
    }

    /**
     * 版本戳（防止延迟清理竞态）
     */
    private final ConcurrentHashMap<UUID, Long> cacheVersions = new ConcurrentHashMap<>();

    /**
     * 缓存清理延迟（ticks）：5分钟 = 6000 ticks
     */
    private static final long CLEANUP_DELAY_TICKS = 5 * 60 * 20;

    /**
     * 画像内存缓存
     */
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    /**
     * 玩家登录时调用：异步加载画像到缓存
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名
     */
    public void onPlayerJoin(UUID uuid, String name) {
        long version = System.nanoTime();
        cacheVersions.put(uuid, version);

        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                PlayerProfile profile = profileDao.loadOrCreate(conn, uuid, name);

                // 在 updateLogin 前判断是否首次登录
                boolean isFirstJoin = profile.getLoginCount() == 0;

                profile.updateLogin(name);
                cache.put(uuid, profile);

                // 异步更新登录信息到 DB
                profileDao.update(conn, profile);

                // 通知事件采集器提交首次加入事件
                if (isFirstJoin && eventCollector != null) {
                    eventCollector.submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_FIRST_JOIN, uuid, name));
                }
            } catch (Exception e) {
                PluginLoggerUtil.error("数据库", "加载玩家画像失败: {} - {}", name, e.getMessage());
            }
        });
    }

    /**
     * 玩家登出时调用：立即异步写回 DB + 调度延迟清理
     *
     * <h3>线程安全说明</h3>
     * <p>本方法在 Bukkit 事件线程（主线程/Region 线程）上执行 {@code profile.updateLogout()} 和
     * {@code profile.addPlaytime()}，然后将同一个 profile 引用提交到 IO 线程异步写入 DB。</p>
     * <p>虽然 PlayerProfile 的字段不是 volatile 的，但 {@code FoliaCompat.getIOPool().submit()}
     * 内部的 {@code ThreadPoolExecutor.execute()} 在入队时产生 happens-before 边界
     * （JSR-133 规范保证：提交任务前的所有写入对工作线程可见），因此 IO 线程能正确读取
     * updateLogout/addPlaytime 的更新结果。</p>
     *
     * @param uuid  玩家 UUID
     * @param world 登出时所在世界
     * @param x     X 坐标
     * @param y     Y 坐标
     * @param z     Z 坐标
     */
    public void onPlayerQuit(UUID uuid, String world, double x, double y, double z) {
        long quitVersion = cacheVersions.getOrDefault(uuid, -1L);

        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            // 更新登出信息（含坐标）
            profile.updateLogout(world, x, y, z);
            long sessionDuration = profile.calculateSessionDuration();
            profile.addPlaytime(sessionDuration);

            // 立即异步写入 DB（不走 Write-Behind，保证可靠性）
            FoliaCompat.getIOPool().submit(() -> {
                try (var conn = databaseManager.getConnection()) {
                    profileDao.update(conn, profile);
                } catch (Exception e) {
                    PluginLoggerUtil.error("数据库", "保存玩家画像失败: {} - {}", uuid, e.getMessage());
                }
            });
        }

        // 延迟5分钟清理缓存（带版本保护）
        FoliaCompat.runTaskLater(plugin, () -> {
            long currentVersion = cacheVersions.getOrDefault(uuid, -1L);
            if (currentVersion == quitVersion) {
                // 版本一致，说明没有重连，安全移除
                cache.remove(uuid);
                cacheVersions.remove(uuid);
            }
            // 版本不一致 → 已重连，跳过移除
        }, CLEANUP_DELAY_TICKS);
    }

    /**
     * 获取玩家画像（缓存优先，未命中时从 DB 加载）
     *
     * @param uuid     玩家 UUID
     * @param callback 异步回调
     */
    public void getProfile(UUID uuid, Consumer<PlayerProfile> callback) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 缓存未命中，异步从 DB 加载
        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                PlayerProfile profile = profileDao.loadByUuid(conn, uuid);
                if (profile != null) {
                    cache.put(uuid, profile);
                }
                callback.accept(profile);
            } catch (Exception e) {
                PluginLoggerUtil.error("数据库", "加载画像失败: {} - {}", uuid, e.getMessage());
                callback.accept(null);
            }
        });
    }

    /**
     * 同步获取缓存中的画像（无 DB 回填）
     *
     * @param uuid 玩家 UUID
     * @return 画像，缓存不存在返回 null
     */
    public PlayerProfile getCachedProfile(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * 更新问候时间（仅更新内存缓存，DB 持久化由 onPlayerQuit 统一负责）
     *
     * <p>不提交独立的异步 DB 写入任务，避免与 onPlayerQuit 的全字段 update 产生竞态条件。
     * 如果 LLM 回复恰好在玩家退出时返回，onPlayerQuit 的 update(conn, profile) 会覆盖
     * last_greeting_time。依赖 onPlayerQuit 统一刷盘即可保证最终一致性。</p>
     *
     * @param uuid 玩家 UUID
     */
    public void updateGreetingTime(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            profile.setLastGreetingTime(System.currentTimeMillis());
        }
    }

    /**
     * 同步刷盘所有在线玩家的画像到 DB（onDisable 兜底）
     * <p>使用单个连接批量更新，避免 N 个玩家 N 次连接 open/close。</p>
     */
    public void flushAllProfiles() {
        if (cache.isEmpty()) return;

        try (var conn = databaseManager.getConnection()) {
            int success = 0;
            boolean connectionAlive = true;
            for (var entry : cache.entrySet()) {
                if (!connectionAlive) break;
                try {
                    profileDao.update(conn, entry.getValue());
                    success++;
                } catch (Exception e) {
                    PluginLoggerUtil.error("数据库", "flush 画像失败: {} - {}", entry.getKey(), e.getMessage());
                    // 连接级别异常（如连接中断），后续更新必然失败，提前退出
                    if (e instanceof SQLException) {
                        connectionAlive = false;
                        PluginLoggerUtil.warn("数据库", "数据库连接异常，剩余 {} 个画像未刷盘", cache.size() - success);
                    }
                }
            }
            PluginLoggerUtil.info("数据库", "已刷盘 {} 个玩家画像（成功 {}）", cache.size(), success);
        } catch (Exception e) {
            PluginLoggerUtil.error("数据库", "获取数据库连接失败，无法刷盘画像: {}", e.getMessage());
        }
    }

    /**
     * 更新玩家画像的扩展数据（画像分析服务调用）
     *
     * <p>同时更新内存缓存、异步写入 DB（使用专用 updateProfileData，只写 profile_data + profile_analyzed_at），
     * 并插入一条快照记录到 {@code kca_profile_snapshot} 表。</p>
     *
     * @param uuid         玩家 UUID
     * @param data         分析结果数据
     * @param analyzedAt   分析完成时间戳
     * @param messageCount 本次分析的消息数
     * @param windowStart  分析窗口起始时间（ms）
     * @param windowEnd    分析窗口截止时间（ms）
     * @param version      画像版本号
     */
    public void putExtendedData(UUID uuid, Map<String, Object> data, long analyzedAt, int messageCount, long windowStart, long windowEnd, int version) {
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            profile.setExtendedData(data);
            profile.setProfileAnalyzedAt(analyzedAt);
        }

        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                profileDao.updateProfileData(conn, uuid, data, analyzedAt);
                snapshotDao.insert(conn, uuid, data, messageCount, windowStart, windowEnd, version, analyzedAt);
            } catch (Exception e) {
                PluginLoggerUtil.error("数据库", I18nService.tr("更新画像数据失败: {} - {}", uuid, e.getMessage()), e);
            }
        });
    }

    /**
     * 构建玩家画像摘要文本（用于注入 LLM 系统提示词）
     *
     * <p>从内存缓存中提取画像数据，生成人类可读的摘要段落。
     * 无画像数据时返回空字符串，调用方可安全拼接。</p>
     *
     * @param uuid 玩家 UUID
     * @return 画像摘要文本，无数据时返回空字符串
     */
    public String buildProfileSummary(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) return "";

        Map<String, Object> data = profile.getExtendedData();
        if (data == null || data.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        String header = I18nService.tr("【玩家画像】\n（注：描述中若包含具体英文词，请结合上下文语义判断是否为玩家名称）");
        sb.append(header);

        appendIfPresent(sb, I18nService.tr("游戏风格"), data.get("playstyle"));
        appendIfPresent(sb, I18nService.tr("性格特征"), data.get("personality"));
        appendIfPresent(sb, I18nService.tr("兴趣偏好"), data.get("interests"));
        appendIfPresent(sb, I18nService.tr("交互禁忌"), data.get("boundaries"));
        appendIfPresent(sb, I18nService.tr("回复偏好"), data.get("communication"));
        appendIfPresent(sb, I18nService.tr("空间记忆"), data.get("spatial"));
        appendIfPresent(sb, I18nService.tr("已知事实"), data.get("facts"));
        appendIfPresent(sb, I18nService.tr("特别观察"), data.get("notes"));

        // 兼容旧画像：过渡期渲染旧字段避免信息丢失（增量分析后旧 key 自然消失）
        appendIfPresent(sb, I18nService.tr("偏好（旧）"), data.get("preferences"));
        appendIfPresent(sb, I18nService.tr("沟通风格（旧）"), data.get("communication_style"));

        if (sb.length() == header.length()) {
            return "";
        }

        sb.append(I18nService.tr("\n以上信息仅供参考，帮助更好地理解玩家。请优先响应当前对话内容。"));

        return sb.toString();
    }

    /**
     * 将玩家画像摘要注入系统提示词（如果配置开启且有画像数据）
     *
     * <p>封装了画像注入的完整逻辑：配置开关检查 → 画像数据获取 → 拼接。
     * 所有面向玩家的 LLM 输出路径统一使用此方法。</p>
     *
     * @param systemPrompt 原始系统提示词
     * @param uuid         玩家 UUID
     * @return 注入画像后的系统提示词（无数据时原样返回）
     */
    public String injectProfileSummary(String systemPrompt, UUID uuid) {
        if (uuid == null || !plugin.getConfigManager().isProfileInjectionEnabled()) {
            return systemPrompt;
        }
        String profileSummary = buildProfileSummary(uuid);
        if (profileSummary.isEmpty()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n" + profileSummary;
    }

    /**
     * 数据库热重载后补录在线玩家画像，并从新库重新加载画像数据到内存缓存
     *
     * <p>场景：从 H2 切换到 MySQL（或反向）后，新数据库的 player_profile 表可能为空或已有数据。</p>
     *
     * <p>策略：遍历内存缓存，对每个在线玩家：</p>
     * <ol>
     *   <li>在新库中 loadOrCreate（不存在则 INSERT 默认记录）</li>
     *   <li>用缓存中的运行时状态（登录次数、坐标、在线时长等）更新新库</li>
     *   <li>从新库重新加载画像到内存缓存（获取新库中的 extendedData、版本号、profileAnalyzedAt 等）</li>
     * </ol>
     *
     * <p>步骤 3 确保切换到已有数据的新库时，内存中的画像版本号等信息与新库一致，
     * 避免后续画像分析从错误的版本号开始。</p>
     */
    public void reconcileOnlineProfiles() {
        if (cache.isEmpty()) return;

        // 同步执行：确保 reload 返回时所有在线玩家画像已在新库中就绪，
        // 避免异步窗口期内玩家退服导致 UPDATE 丢失。
        // 使用单个连接批量操作（与 flushAllProfiles 一致），避免 N 次建连开销。
        int count = 0;
        boolean connectionAlive = true;
        try (var conn = databaseManager.getConnection()) {
            for (var entry : cache.entrySet()) {
                if (!connectionAlive) break;
                // 仅补录真正在线的玩家，跳过已下线但缓存尚未清理的画像
                if (plugin.getServer().getPlayer(entry.getKey()) == null) {
                    continue;
                }
                try {
                    PlayerProfile cached = entry.getValue();
                    profileDao.loadOrCreate(conn, cached.getUuid(), cached.getName());
                    profileDao.update(conn, cached);
                    PlayerProfile dbProfile = profileDao.loadByUuid(conn, cached.getUuid());
                    if (dbProfile != null) {
                        dbProfile.setLastLogin(cached.getLastLogin());
                        dbProfile.setLoginCount(cached.getLoginCount());
                        dbProfile.setTotalPlaytimeMs(cached.getTotalPlaytimeMs());
                        dbProfile.setLastGreetingTime(cached.getLastGreetingTime());
                        entry.setValue(dbProfile);
                    }
                    count++;
                } catch (Exception e) {
                    PluginLoggerUtil.warn("数据库", "热重载后补录画像失败: {} - {}", entry.getValue().getName(), e.getMessage());
                    // 连接死亡则停止后续补录，避免 N 次无效尝试
                    if (e instanceof SQLException) connectionAlive = false;
                }
            }
        } catch (Exception e) {
            PluginLoggerUtil.warn("数据库", "获取数据库连接失败，无法补录画像: {}", e.getMessage());
            return;
        }
        if (count > 0) {
            PluginLoggerUtil.info("数据库", "热重载后补录 {} 个在线玩家画像", count);
        }
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value == null) return;
        String str = value.toString().trim();
        if (str.isEmpty()) return;
        sb.append("\n").append(label).append(": ").append(str);
    }
}
