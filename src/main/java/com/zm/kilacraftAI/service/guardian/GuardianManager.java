package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.GuardianConfigManager;
import com.zm.kilacraftAI.config.GuardianConfigManager.MonitorTemplate;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.GuardianProfileDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守护系统生命周期管理器。单例，由 {@code onEnable} 创建。
 *
 * <p>职责：每玩家 Guardian 的创建/销毁、默认套餐启用/停用、玩家反馈（静音）。
 * 配置与静音列表持久化到 {@code guardian_profile} 表，重启后按玩家上线恢复。</p>
 *
 * <p>线程模型：玩家操作（命令/skill）在主线程或 IO 线程；GuardianEngine 心跳读 guardians map。
 * ConcurrentHashMap 保证并发读写安全；Guardian 内部 monitor 列表在注册时确定后不变。
 * 持久化全部走 IO 线程池异步写入，失败仅告警不影响主流程。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class GuardianManager {

    private final KilacraftAI plugin;
    private final GuardianConfigManager configManager;
    private final MonitorFactory monitorFactory;
    private final GuardianEngine engine;
    private final DatabaseManager databaseManager;
    private final GuardianProfileDao profileDao;
    /** 玩家 → Guardian。Guardian 含 hub + monitors。 */
    private final Map<UUID, Guardian> guardians = new ConcurrentHashMap<>();
    /** 玩家 → 是否启用守护（opt-in）。与 guardians 分离：禁用时 Guardian 暂停但配置保留。 */
    private final Map<UUID, Boolean> enabled = new ConcurrentHashMap<>();

    public GuardianManager(KilacraftAI plugin, GuardianConfigManager configManager,
                           MonitorFactory monitorFactory, GuardianEngine engine,
                           DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.monitorFactory = monitorFactory;
        this.engine = engine;
        this.databaseManager = databaseManager;
        this.profileDao = databaseManager != null
                ? new GuardianProfileDao(databaseManager.getTablePrefix())
                : null;
    }

    /** 玩家是否启用了守护（opt-in，默认关闭）。 */
    public boolean isGuardianEnabled(UUID playerId) {
        return enabled.getOrDefault(playerId, false);
    }

    /**
     * 启用默认套餐（/kila guardian on）。
     *
     * <p>若已有 Guardian 则先清空重建（套餐可能因 reload 变化）。
     * 返回创建成功的 monitor 名列表（供命令反馈）。全局开关关闭时返回 empty。</p>
     */
    public synchronized Optional<List<String>> enable(Player player) {
        UUID id = player.getUniqueId();
        if (!configManager.isEnabled()) {
            return Optional.empty();
        }
        // restoreGuardian 经 runTask 延迟调度，玩家可能在排队期间下线——abort 避免为离线玩家注册 Guardian
        if (!player.isOnline()) {
            return Optional.empty();
        }
        // 清除旧 Guardian 并取回其静音列表，回填到新 hub（保留玩家反馈配置）
        Set<AlertCategory> preservedSilenced = disableInternal(id);

        GuardianCooldownHub hub = new GuardianCooldownHub(
                configManager.getGlobalCooldownMillis(),
                configManager.getCategoryCooldownMillis(),
                cat -> true);
        for (AlertCategory cat : preservedSilenced) {
            hub.silence(cat);
        }
        List<Monitor> monitors = new ArrayList<>();
        List<String> created = new ArrayList<>();
        for (String tmplName : configManager.getDefaultBundle()) {
            MonitorTemplate tmpl = configManager.getTemplate(tmplName);
            if (tmpl == null) {
                PluginLoggerUtil.warn("守护系统", I18nService.tr("默认套餐引用了不存在的模板: {}", tmplName));
                continue;
            }
            Optional<Monitor> m = monitorFactory.create(tmpl, null);
            if (m.isPresent()) {
                monitors.add(m.get());
                created.add(tmplName);
            }
        }
        if (monitors.isEmpty()) {
            PluginLoggerUtil.warn("守护系统", I18nService.tr("玩家 {} 启用守护失败：默认套餐未创建任何 monitor（检查 guardian.yml 模板配置）", player.getName()));
            return Optional.of(created);
        }
        Guardian guardian = new Guardian(id, monitors, hub);
        guardians.put(id, guardian);
        enabled.put(id, true);
        engine.registerGuardian(id, guardian);
        PluginLoggerUtil.info("守护系统", I18nService.tr("玩家 {} 启用守护，{} 个 monitor", player.getName(), created.size()));
        persistProfile(id, true, hub.silencedCategories());
        return Optional.of(created);
    }

    /** 停用守护。静音列表保留供重新启用。 */
    public synchronized void disable(UUID playerId) {
        disableInternal(playerId);
    }

    /** disable 的内部实现，返回旧 Guardian 的静音列表供 enable 回填。 */
    private Set<AlertCategory> disableInternal(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        Set<AlertCategory> silenced = g != null ? g.hub().silencedCategories() : Set.of();
        if (g != null) {
            engine.unregisterGuardian(playerId);
        }
        enabled.put(playerId, false);
        persistProfile(playerId, false, silenced);
        return silenced;
    }

    /**
     * 玩家上线时从 guardian_profile 恢复守护状态。
     *
     * <p>DB 读取走 IO 线程；若 enabled=true 则恢复默认套餐 monitor 并重放静音列表。
     * DB 不可用时直接退化为内存态（重启后默认关闭）。</p>
     */
    public void onPlayerJoin(Player player) {
        if (databaseManager == null || profileDao == null) {
            return;
        }
        UUID id = player.getUniqueId();
        String uuid = id.toString();
        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                boolean wasEnabled = profileDao.isEnabled(conn, uuid);
                if (!wasEnabled) {
                    return;
                }
                Set<String> silenceList = profileDao.loadSilenceList(conn, uuid);
                FoliaCompat.runTask(plugin, () -> restoreGuardian(player, silenceList));
            } catch (Exception e) {
                PluginLoggerUtil.warn("守护系统", I18nService.tr("恢复玩家守护配置失败（{}）: {}", player.getName(), e.getMessage()));
            }
        });
    }

    /** 玩家下线时清理（释放引擎资源、per-player lock、预算窗口）。synchronized 与 enable/disable 互斥。 */
    public synchronized void onPlayerQuit(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        if (g != null) {
            engine.unregisterGuardian(playerId);
        }
        enabled.remove(playerId);
        if (plugin.getLlmOutputCoordinator() != null) {
            plugin.getLlmOutputCoordinator().getBudgetManager().clearPlayer(playerId);
        }
    }

    /** 玩家反馈：静音一个分类（"别提醒我 X"）。 */
    public void silence(UUID playerId, AlertCategory category) {
        Guardian g = guardians.get(playerId);
        if (g != null) {
            g.hub().silence(category);
            persistSilenceList(playerId, g.hub().silencedCategories());
        }
    }

    /** 玩家反馈：取消静音（"X 可以提醒了"）。 */
    public void unsilence(UUID playerId, AlertCategory category) {
        Guardian g = guardians.get(playerId);
        if (g != null) {
            g.hub().unsilence(category);
            persistSilenceList(playerId, g.hub().silencedCategories());
        }
    }

    public Guardian getGuardian(UUID playerId) {
        return guardians.get(playerId);
    }

    /** 玩家当前静音的分类列表（空集=未静音或守护未启用）。 */
    public Set<AlertCategory> getSilencedCategories(UUID playerId) {
        Guardian g = guardians.get(playerId);
        return g != null ? g.hub().silencedCategories() : Set.of();
    }

    /**
     * 在主线程重放持久化的守护状态：启用默认套餐并恢复静音列表。
     * 由 onPlayerJoin 的 DB 读取回调调度到此。
     */
    private void restoreGuardian(Player player, Set<String> silenceList) {
        Optional<List<String>> created = enable(player);
        if (created.isEmpty()) {
            return;
        }
        Guardian g = guardians.get(player.getUniqueId());
        if (g != null) {
            for (String name : silenceList) {
                try {
                    g.hub().silence(AlertCategory.valueOf(name));
                } catch (IllegalArgumentException e) {
                    PluginLoggerUtil.warn("守护系统", I18nService.tr("玩家 {} 的守护静音配置含无效分类 {}，已跳过", player.getName(), name));
                }
            }
        }
    }

    /** 异步写整行 guardian_profile（enabled + 静音列表）。DB 不可用时为空操作。 */
    private void persistProfile(UUID playerId, boolean isEnabled, Set<AlertCategory> silenced) {
        if (profileDao == null || databaseManager == null) {
            return;
        }
        String uuid = playerId.toString();
        String silenceList = formatSilenceList(silenced);
        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                profileDao.upsert(conn, uuid, isEnabled, null, silenceList, System.currentTimeMillis());
            } catch (Exception e) {
                PluginLoggerUtil.warn("守护系统", I18nService.tr("持久化守护配置失败（{}）: {}", playerId, e.getMessage()));
            }
        });
    }

    /** 仅更新静音列表（沿用提交时刻的 enabled 状态）。DB 不可用时为空操作。 */
    private void persistSilenceList(UUID playerId, Set<AlertCategory> silenced) {
        if (profileDao == null || databaseManager == null) {
            return;
        }
        String uuid = playerId.toString();
        String silenceList = formatSilenceList(silenced);
        // 提交时捕获 enabled 快照，避免执行时状态已变（on/off 快速操作致异步写乱序）
        boolean enabledSnapshot = isGuardianEnabled(playerId);
        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                profileDao.upsert(conn, uuid, enabledSnapshot, null, silenceList, System.currentTimeMillis());
            } catch (Exception e) {
                PluginLoggerUtil.warn("守护系统", I18nService.tr("持久化静音列表失败（{}）: {}", playerId, e.getMessage()));
            }
        });
    }

    private static String formatSilenceList(Set<AlertCategory> silenced) {
        if (silenced == null || silenced.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (AlertCategory c : silenced) {
            joiner.add(c.name());
        }
        return joiner.toString();
    }

    /** 关闭：清理所有 Guardian（onDisable 调用，须在 taskScheduler.shutdownAll 之前）。 */
    public void shutdown() {
        for (UUID id : new ArrayList<>(guardians.keySet())) {
            Guardian g = guardians.remove(id);
            if (g != null) {
                engine.unregisterGuardian(id);
            }
        }
        enabled.clear();
    }

    /** 在线启用了守护的玩家数（统计/调试用）。 */
    public int activeCount() {
        return guardians.size();
    }
}
