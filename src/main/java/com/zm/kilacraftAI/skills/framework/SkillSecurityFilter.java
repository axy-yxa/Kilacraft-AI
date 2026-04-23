package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能安全过滤器 - 基于 Value 扫描的玩家数据隔离
 *
 * <p>在技能执行前进行安全校验，覆盖所有内置Skill和通过SPI集成的第三方Skill。</p>
 * <p><b>拦截器始终运行，不可跳过。</b></p>
 *
 * <h3>核心策略：Value扫描（非合作式）</h3>
 * <p>不依赖Skill声明任何参数名，直接扫描entities中所有Value。</p>
 * <p>如果某个Value匹配在线玩家名且不是当前玩家自己，且不在白名单中，则消毒（替换为当前玩家名）。</p>
 *
 * <h3>为什么不基于Key声明？</h3>
 * <p>基于Key的校验是合作式的——恶意Skill可以通过篡改声明绕过。</p>
 * <p>基于Value的扫描是非合作式的——无论Skill怎么声明，只要Value是在线玩家名就会被检测到。</p>
 *
 * <h3>线程安全</h3>
 * <p>通过事件驱动的在线玩家名缓存，避免在异步线程调用Bukkit API。</p>
 * <p>缓存由主线程写入(PlayerJoinEvent/PlayerQuitEvent)，异步线程只读。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public class SkillSecurityFilter implements Listener {

    /**
     * 在线玩家名缓存（线程安全，由事件驱动更新）
     */
    private static final Set<String> ONLINE_PLAYER_NAMES = ConcurrentHashMap.newKeySet();

    /**
     * Minecraft玩家名合法性正则：1-16字符，只允许a-z A-Z 0-9 _
     */
    private static final java.util.regex.Pattern PLAYER_NAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    private SkillSecurityFilter() {
        // 工具类禁止实例化（但实现了Listener，需由外部创建实例注册事件）
    }

    /**
     * 创建过滤器实例并初始化在线玩家缓存
     *
     * <p>应在插件onEnable时调用，同时注册事件监听器。</p>
     *
     * @return 过滤器实例（用于注册为Listener）
     */
    public static SkillSecurityFilter createAndInit() {
        SkillSecurityFilter filter = new SkillSecurityFilter();
        // 初始化：加载当前所有在线玩家
        ONLINE_PLAYER_NAMES.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ONLINE_PLAYER_NAMES.add(player.getName());
        }
        return filter;
    }

    // ==================== 事件驱动缓存更新 ====================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ONLINE_PLAYER_NAMES.add(event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ONLINE_PLAYER_NAMES.remove(event.getPlayer().getName());
    }

    // ==================== 安全校验核心 ====================

    /**
     * 校验并消毒技能执行的entities参数
     *
     * <p>遍历entities中所有Value，如果某个Value匹配在线玩家名且不是当前玩家自己，
     * 且不在白名单中，则替换为当前玩家名（消毒）。</p>
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

        // 获取合并后的白名单（内置 + config）
        Set<String> allowedActions = getAllowedActions();

        // 遍历所有Value，检测并消毒
        Map<String, String> sanitized = null; // 延迟创建，避免不必要的复制
        for (Map.Entry<String, String> entry : entities.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }

            // 快速排除：不符合玩家名格式的值
            // 参数格式由YML提示词定义，LLM按结构化格式生成，玩家名一定是独立Value
            if (!couldBePlayerName(value)) {
                continue;
            }

            // 是当前玩家自己，放行
            if (value.equals(playerName)) {
                continue;
            }

            // 匹配到在线玩家名 → 需要检查白名单
            if (ONLINE_PLAYER_NAMES.contains(value)) {
                if (allowedActions.contains(actionKey) || allowedActions.contains(skillName)) {
                    PluginLogger.debug("安全拦截", "白名单放行：{} → {}", actionKey, value);
                    continue;
                }

                // 消毒：替换为当前玩家名
                if (sanitized == null) {
                    sanitized = new java.util.HashMap<>(entities);
                }
                sanitized.put(entry.getKey(), playerName);
                PluginLogger.warn("安全拦截", I18nService.tr("玩家数据隔离：entities[{}]={} 是其他在线玩家，已替换为当前玩家 {}", entry.getKey(), value, playerName));
            }
        }

        return sanitized != null ? sanitized : entities;
    }

    // ==================== 工具方法 ====================

    /**
     * 快速判断一个值是否可能是玩家名
     *
     * <p>Minecraft玩家名规则：1-16字符，只允许 a-z A-Z 0-9 _</p>
     * <p>不满足此格式的值直接跳过，避免对在线玩家集合的无意义查找。</p>
     */
    private static boolean couldBePlayerName(String value) {
        return PLAYER_NAME_PATTERN.matcher(value).matches();
    }

    /**
     * 获取白名单（从 config.yml 动态配置）
     *
     * <p>白名单统一由 config.yml 的 security.player_isolation.allowed_actions 管理，
     * 支持通过 /kilacraft reload 热更新。</p>
     */
    private static Set<String> getAllowedActions() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return Set.of();
        }

        java.util.List<String> configAllowed = plugin.getConfigManager().getSecurityAllowedActions();
        if (configAllowed == null || configAllowed.isEmpty()) {
            return Set.of();
        }
        return new java.util.HashSet<>(configAllowed);
    }
}
