package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能安全过滤器 - 基于 Value 扫描的玩家数据隔离
 *
 * <p>在技能执行前进行安全校验，覆盖所有内置Skill和通过SPI集成的第三方Skill。</p>
 * <p><b>拦截器始终运行，不可跳过。</b></p>
 *
 * <h3>核心策略：Value扫描（非合作式）</h3>
 * <p>不依赖Skill声明任何参数名，直接扫描entities中所有Value。</p>
 * <p>如果某个Value匹配其他玩家名（在线或近期活跃的离线玩家）且不在白名单中，
 * 则消毒（替换为当前玩家名）。</p>
 *
 * <h3>为什么不基于Key声明？</h3>
 * <p>基于Key的校验是合作式的——恶意Skill可以通过篡改声明绕过。</p>
 * <p>基于Value的扫描是非合作式的——无论Skill怎么声明，只要Value是已知玩家名就会被检测到。</p>
 *
 * <h3>近期活跃玩家缓存</h3>
 * <p>除在线玩家外，额外维护一个有界的"近期活跃"缓存（在线 + 近期离线统一管理），
 * 堵住"仅扫描在线玩家时，离线玩家名被放行"的缺口。缓存有界（max_size）+ TTL 淘汰，
 * 避免大服历史玩家名全量驻留内存。启动时可从画像表预热近期活跃玩家，弥补重启冷启动窗口。</p>
 *
 * <h3>线程安全</h3>
 * <p>通过事件驱动的玩家名缓存，避免在异步线程调用Bukkit API。</p>
 * <p>缓存由主线程写入(PlayerJoinEvent/PlayerQuitEvent)，异步线程只读。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public class SkillSecurityFilter implements Listener {

    /**
     * 在线玩家名缓存（精确在线集合，线程安全，由事件驱动更新）
     */
    private static final Set<String> ONLINE_PLAYER_NAMES = ConcurrentHashMap.newKeySet();

    /**
     * 在线玩家 UUID→名称 映射（线程安全，由事件驱动更新）
     */
    private static final Map<UUID, String> ONLINE_UUID_TO_NAME = new ConcurrentHashMap<>();

    /**
     * 近期活跃玩家名缓存：name → 最后活跃时间戳(ms)。
     *
     * <p>覆盖"在线 + 近期离线"玩家。PlayerJoin/PlayerQuit 时 put(当前时间)；
     * 退出时不立即移除，让其进入"近期离线"保留窗口，由 {@link #cleanupExpired()} 按 TTL 淘汰。
     * 启动时可从画像表预热近期活跃玩家（{@link #preloadRecentPlayers()}）。</p>
     *
     * <p>用途：sanitize 识别"其他玩家"时，除在线集合外也查此缓存，
     * 防止恶意Skill通过离线玩家名绕过数据隔离。</p>
     */
    private static final Map<String, Long> RECENT_PLAYER_NAMES = new ConcurrentHashMap<>();

    /**
     * Minecraft玩家名合法性正则：1-16字符，只允许a-z A-Z 0-9 _
     */
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    /**
     * 命令 token 拆分：前缀标点 + 核心标识符 + 后缀标点。
     * 用于对复合值（如 "tpa PlayerB"）做 token 级玩家名扫描，保留首尾标点（引号等）。
     */
    private static final Pattern TOKEN_PARTS = Pattern.compile("^([^a-zA-Z0-9_]*)([a-zA-Z0-9_]+)([^a-zA-Z0-9_]*)$");

    private SkillSecurityFilter() {
    }

    /**
     * 创建过滤器实例并初始化玩家缓存
     *
     * <p>应在插件onEnable时调用，同时注册事件监听器。</p>
     *
     * @return 过滤器实例（用于注册为Listener）
     */
    public static SkillSecurityFilter createAndInit() {
        SkillSecurityFilter filter = new SkillSecurityFilter();
        ONLINE_PLAYER_NAMES.clear();
        ONLINE_UUID_TO_NAME.clear();
        RECENT_PLAYER_NAMES.clear();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            ONLINE_PLAYER_NAMES.add(name);
            ONLINE_UUID_TO_NAME.put(player.getUniqueId(), name);
            RECENT_PLAYER_NAMES.put(name, now);
        }
        // 启动预热：从画像表加载近期活跃玩家，弥补冷启动窗口
        preloadRecentPlayers();
        return filter;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        long now = System.currentTimeMillis();
        ONLINE_PLAYER_NAMES.add(name);
        ONLINE_UUID_TO_NAME.put(player.getUniqueId(), name);
        RECENT_PLAYER_NAMES.put(name, now);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        // 不从 RECENT 移除：记录最后活跃时间，让其进入"近期离线"保留窗口，
        // 由周期清理任务按 TTL 淘汰。这样 sanitize 仍能识别刚下线的其他玩家。
        RECENT_PLAYER_NAMES.put(name, System.currentTimeMillis());
        ONLINE_PLAYER_NAMES.remove(name);
        ONLINE_UUID_TO_NAME.remove(player.getUniqueId());
    }

    /**
     * 从画像表预热近期活跃玩家名到缓存（异步）。
     *
     * <p>弥补服务器重启后的冷启动窗口（此刻缓存仅有在线玩家）。
     * 预热的玩家以当前时间戳入缓存，随 TTL 自然过期；期间 PlayerJoin/PlayerQuit 事件
     * 会持续维护真正近期活跃的玩家。</p>
     */
    private static void preloadRecentPlayers() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg == null || !cfg.isSecurityOfflineCachePreload()) return;
        var dbManager = plugin.getDatabaseManager();
        if (dbManager == null) return;

        int preloadDays = Math.max(1, cfg.getSecurityOfflineCachePreloadDays());
        int maxSize = Math.max(1, cfg.getSecurityOfflineCacheMaxSize());
        long afterTime = System.currentTimeMillis() - preloadDays * 86_400_000L;

        FoliaCompat.getIOPool().submit(() -> {
            try (Connection conn = dbManager.getConnection()) {
                PlayerProfileDao dao = new PlayerProfileDao(dbManager.getTablePrefix());
                List<PlayerProfileDao.TopActivePlayer> recent = dao.queryTopActive(conn, afterTime, "last_login", maxSize);
                long now = System.currentTimeMillis();
                int count = 0;
                for (PlayerProfileDao.TopActivePlayer p : recent) {
                    if (p.name() != null) {
                        RECENT_PLAYER_NAMES.put(p.name(), now);
                        count++;
                    }
                }
                PluginLoggerUtil.info("安全拦截", "预热近期活跃玩家缓存：{} 个", count);
            } catch (Exception e) {
                PluginLoggerUtil.warn("安全拦截", "预热近期活跃玩家缓存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 清理过期的近期活跃玩家缓存。
     *
     * <p>由 {@code TaskScheduler} 周期调用（见 KilacraftAI.initializeScheduledTasks）。
     * 两步：① 移除超过 TTL 的条目（在线玩家由 {@link #ONLINE_PLAYER_NAMES} 兜底，RECENT 中清掉不影响 sanitize）；
     * ② 超过 max_size 时按最久未活跃淘汰。</p>
     *
     * @return 本次清理移除的条目数
     */
    public static int cleanupExpired() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null) return 0;
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg == null) return 0;

        long ttlMs = Math.max(1, cfg.getSecurityOfflineCacheTtlMinutes()) * 60_000L;
        int maxSize = Math.max(1, cfg.getSecurityOfflineCacheMaxSize());
        long now = System.currentTimeMillis();
        int removed = 0;

        // 1. 清理过期条目
        for (Map.Entry<String, Long> entry : RECENT_PLAYER_NAMES.entrySet()) {
            if (now - entry.getValue() > ttlMs) {
                if (RECENT_PLAYER_NAMES.remove(entry.getKey()) != null) removed++;
            }
        }
        // 2. 超上限淘汰最旧（保护内存，大服场景）
        while (RECENT_PLAYER_NAMES.size() > maxSize) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : RECENT_PLAYER_NAMES.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) break;
            RECENT_PLAYER_NAMES.remove(oldestKey);
            removed++;
        }
        return removed;
    }

    /**
     * 校验并消毒技能执行的entities参数
     *
     * <p>遍历entities中所有Value：
     * <ul>
     *   <li>单值：若匹配已知玩家名（在线或近期活跃）且非自身、非白名单，则替换为当前玩家名（消毒）。</li>
     *   <li>复合值（含空白，如命令文本）：拆分为 token 逐个扫描，防止把其他玩家名藏进复合参数绕过隔离。</li>
     * </ul>
     * 白名单内的 action 放行（如转账、TPA 等合法跨玩家操作）。</p>
     *
     * @param skillName 技能名称
     * @param action    动作名称
     * @param context   执行上下文
     * @return 消毒后的entities Map（如果无需消毒则返回原始Map），null表示上下文异常
     */
    public static Map<String, String> sanitize(String skillName, String action, SkillContext context) {
        if (context == null) {
            return null;
        }

        Player player = context.getPlayer();
        if (player == null) {
            // 非玩家上下文(如控制台),无需玩家数据隔离
            return context.getEntities();
        }

        Map<String, String> entities = context.getEntities();
        if (entities == null || entities.isEmpty()) {
            return entities;
        }

        // 检查功能开关
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin != null && plugin.getConfigManager() != null && !plugin.getConfigManager().isSecurityPlayerIsolationEnabled()) {
            return entities; // 功能关闭，不消毒
        }

        String playerName = player.getName();
        String actionKey = skillName + "." + action;
        Set<String> allowedActions = getAllowedActions();
        boolean whitelisted = allowedActions.contains(actionKey) || allowedActions.contains(skillName);

        // 遍历所有Value，检测并消毒
        Map<String, String> sanitized = null; // 延迟创建，避免不必要的复制
        for (Map.Entry<String, String> entry : entities.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }

            // 复合值（含空白，如命令文本 "tpa PlayerB"）：整体不符合玩家名格式，
            // 需拆分为 token 逐个扫描，防止把其他玩家名藏进命令/复合参数绕过隔离。
            // 白名单 action（如 command.execute_command，OP-only 受信操作）豁免。
            if (value.indexOf(' ') >= 0) {
                if (whitelisted) {
                    continue;
                }
                String cleaned = sanitizeCommandTokens(value, playerName);
                if (cleaned != null) {
                    if (sanitized == null) {
                        sanitized = new HashMap<>(entities);
                    }
                    sanitized.put(entry.getKey(), cleaned);
                }
                continue;
            }

            // 单值：整体扫描
            // 快速排除：不符合玩家名格式的值（参数格式由YML提示词定义，LLM按结构化格式生成）
            if (!couldBePlayerName(value)) {
                continue;
            }
            // 是当前玩家自己，放行
            if (value.equals(playerName)) {
                continue;
            }
            // 非已知玩家（既不在线也不在近期活跃缓存），放行
            if (!isKnownPlayer(value)) {
                continue;
            }
            if (whitelisted) {
                PluginLoggerUtil.debug("安全拦截", "白名单放行：{} → {}", actionKey, value);
                continue;
            }

            // 消毒：替换为当前玩家名
            if (sanitized == null) {
                sanitized = new HashMap<>(entities);
            }
            sanitized.put(entry.getKey(), playerName);
            PluginLoggerUtil.warn("安全拦截", "玩家数据隔离：entities[{}]={} 是其他玩家，已替换为当前玩家 {}", entry.getKey(), value, playerName);
        }

        return sanitized != null ? sanitized : entities;
    }

    /**
     * 对复合字符串值（如命令文本）做 token 级玩家名扫描。
     *
     * <p>按空白拆分，对每个 token 的核心标识符检查是否为其他已知玩家，
     * 命中则替换为当前玩家名（保留首尾标点）。防止恶意Skill把其他玩家名藏进
     * 复合参数值（命令、描述等）绕过单值扫描。</p>
     *
     * @param value      复合值（含空白）
     * @param playerName 当前玩家名
     * @return 替换后的字符串；无需替换返回 null
     */
    private static String sanitizeCommandTokens(String value, String playerName) {
        String[] parts = value.split(" ");
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String rebuilt = sanitizeToken(parts[i], playerName);
            if (rebuilt != null) {
                parts[i] = rebuilt;
                changed = true;
            }
        }
        return changed ? String.join(" ", parts) : null;
    }

    /**
     * 扫描单个 token：若核心标识符是其他已知玩家名则替换为当前玩家名，保留首尾标点。
     *
     * @param raw        原始 token（可能带引号/标点）
     * @param playerName 当前玩家名
     * @return 替换后的 token；无需替换返回 null
     */
    private static String sanitizeToken(String raw, String playerName) {
        Matcher m = TOKEN_PARTS.matcher(raw);
        if (!m.matches()) {
            return null;
        }
        String core = m.group(2);
        if (!couldBePlayerName(core)) {
            return null;
        }
        if (core.equals(playerName)) {
            return null;
        }
        if (!isKnownPlayer(core)) {
            return null;
        }
        PluginLoggerUtil.warn("安全拦截", "复合参数玩家数据隔离：token {} → {}", core, playerName);
        return m.group(1) + playerName + m.group(3);
    }

    /**
     * 获取当前在线玩家 UUID→名称映射（线程安全，只读视图）
     *
     * <p>供社交关系模块将 target_uuid 反查为在线玩家名。</p>
     *
     * @return 在线玩家 UUID→名称映射的快照（不可修改）
     */
    public static Map<UUID, String> getOnlineUuidToName() {
        return Map.copyOf(ONLINE_UUID_TO_NAME);
    }

    /**
     * 值是否为已知的其他玩家（在线或近期活跃的离线玩家）
     */
    private static boolean isKnownPlayer(String value) {
        return ONLINE_PLAYER_NAMES.contains(value) || RECENT_PLAYER_NAMES.containsKey(value);
    }

    /**
     * 快速判断一个值是否可能是玩家名
     *
     * <p>Minecraft玩家名规则：1-16字符，只允许 a-z A-Z 0-9 _</p>
     * <p>不满足此格式的值直接跳过，避免对玩家集合的无意义查找。</p>
     */
    private static boolean couldBePlayerName(String value) {
        return PLAYER_NAME_PATTERN.matcher(value).matches();
    }

    /**
     * 获取白名单（从 config.yml 动态配置）
     *
     * <p>白名单统一由 config.yml 的 security.player_isolation.allowed_actions 管理，
     * 支持通过 /kila reload 热更新。</p>
     */
    private static Set<String> getAllowedActions() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return Set.of();
        }

        List<String> configAllowed = plugin.getConfigManager().getSecurityAllowedActions();
        if (configAllowed == null || configAllowed.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(configAllowed);
    }
}
