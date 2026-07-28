package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 玩家自定义监听系统配置管理器。
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public class WatchConfigManager {

    private static final String CONFIG_FILE = "watch.yml";
    private static final long MIN_POLL_INTERVAL_TICKS = 20L;

    private final KilacraftAI plugin;

    @Getter
    private volatile boolean enabled = true;
    @Getter
    private volatile long pollIntervalTicks = 600L;
    @Getter
    private volatile int offlineGraceMinutes = 5;
    @Getter
    private volatile int maxPollingWatches = 3;
    @Getter
    private volatile int maxEventWatches = 5;
    @Getter
    private volatile int maxWatchesGlobal = 200;
    @Getter
    private volatile int triggerCooldownSeconds = 30;

    public WatchConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = yaml.getBoolean("watch.enabled", true);
        long interval = yaml.getLong("watch.poll_interval_ticks", 600L);
        this.pollIntervalTicks = Math.max(MIN_POLL_INTERVAL_TICKS, interval);
        this.offlineGraceMinutes = Math.max(0, yaml.getInt("watch.offline_grace_minutes", 5));
        this.maxPollingWatches = Math.max(1, yaml.getInt("watch.max_polling_watches", 3));
        this.maxEventWatches = Math.max(1, yaml.getInt("watch.max_event_watches", 5));
        this.maxWatchesGlobal = Math.max(1, yaml.getInt("watch.max_watches_global", 200));
        this.triggerCooldownSeconds = Math.max(0, yaml.getInt("watch.trigger_cooldown_seconds", 30));
    }

    public void reload() {
        loadConfig();
    }
}
