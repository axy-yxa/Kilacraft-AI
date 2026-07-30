package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.GuardianConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.scheduler.TaskScheduler;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守护系统生命周期管理器。
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class GuardianManager {

    private final KilacraftAI plugin;
    private final GuardianConfigManager configManager;
    private final GuardianEngine engine;
    /**
     * 玩家 → Guardian。
     */
    private final ConcurrentHashMap<UUID, Guardian> guardians = new ConcurrentHashMap<>();
    /**
     * 玩家 → 是否启用守护（opt-in，内存态）。与 guardians 分离：禁用时 Guardian 暂停但配置保留。
     */
    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();

    public GuardianManager(KilacraftAI plugin, GuardianConfigManager configManager, GuardianEngine engine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.engine = engine;
    }

    /**
     * 玩家是否启用了守护（opt-in，默认关闭）。
     */
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
        if (!player.isOnline()) {
            return Optional.empty();
        }
        // 清除旧 Guardian（套餐可能因 reload 变化）
        disableInternal(id);

        // 默认套餐：轮询型（背包空间/装备耐久）+ 事件型（威胁锁定）。
        List<Monitor> monitors = new ArrayList<>();
        monitors.addAll(BuiltInMonitors.createDefaultPollingMonitors(configManager));
        monitors.addAll(BuiltInMonitors.createEventMonitors(configManager));
        Guardian guardian = new Guardian(monitors);
        guardians.put(id, guardian);
        enabled.put(id, true);
        engine.registerGuardian(id, guardian);
        // 返回的 id 列表用于命令反馈
        List<String> resultNames = monitors.stream().map(Monitor::id).toList();
        return Optional.of(resultNames);
    }

    /**
     * 停用守护。
     */
    public synchronized void disable(UUID playerId) {
        disableInternal(playerId);
    }

    private void disableInternal(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        if (g != null) {
            engine.unregisterGuardian(playerId);
            // 中断旧 monitor 的在途 LLM 动作，避免 reload/disable 后收到过时告警
            cancelInFlightGuardianActions(playerId);
        }
        enabled.put(playerId, false);
    }

    /**
     * 中断该玩家在途的守护 LLM 调用 + 收尾流式 UI（reload/disable 时抑制旧 monitor 的延迟告警）。
     */
    private void cancelInFlightGuardianActions(UUID playerId) {
        if (plugin.getLlmManager() == null || plugin.getLlmManager().getCurrentProvider() == null) {
            return;
        }
        plugin.getLlmManager().getCurrentProvider().cancelInFlight(playerId);
        if (plugin.getResponsePipeline() != null) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                plugin.getResponsePipeline().cancelStream(p);
            }
        }
    }

    /**
     * 玩家下线时清理（释放引擎资源、per-player lock、预算窗口）。synchronized 与 enable/disable 互斥。
     */
    public synchronized void onPlayerQuit(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        if (g != null) {
            engine.unregisterGuardian(playerId);
            cancelInFlightGuardianActions(playerId);
        }
        enabled.remove(playerId);
        // LLM 预算窗口的清理由 ChatListener.onPlayerQuit 统一负责（独立于守护系统生命周期）
    }

    public Guardian getGuardian(UUID playerId) {
        return guardians.get(playerId);
    }

    /**
     * reload 后重建所有在线已启用玩家的 Guardian（套餐/参数可能变化）。
     * 由 /kila reload 调用。在主线程执行，与 enable/disable synchronized 互斥。
     */
    public synchronized void reloadAll() {
        List<UUID> onlineIds = new ArrayList<>();
        for (UUID id : guardians.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                onlineIds.add(id);
            }
        }
        for (UUID id : onlineIds) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                enable(p);
            }
        }
        PluginLoggerUtil.info("守护系统", I18nService.tr("reload 完成，重建 {} 个在线玩家的守护", onlineIds.size()));

        // 心跳间隔可能变化：取消旧定时器、用新 intervalTicks 重新注册
        TaskScheduler scheduler = plugin.getTaskScheduler();
        if (scheduler != null) {
            scheduler.unregister(engine);
            scheduler.register(engine);
        }
    }

    /**
     * 关闭：清理所有 Guardian（onDisable 调用，须在 taskScheduler.shutdownAll 之前）。
     */
    public void shutdown() {
        for (UUID id : new ArrayList<>(guardians.keySet())) {
            Guardian g = guardians.remove(id);
            if (g != null) {
                engine.unregisterGuardian(id);
            }
        }
        enabled.clear();
    }

    /**
     * 在线启用了守护的玩家数（统计/调试用）。
     */
    public int activeCount() {
        return guardians.size();
    }
}
