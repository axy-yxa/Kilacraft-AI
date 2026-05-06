package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.greeting.GreetingPromptBuilder;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * AI 登录问候配置管理器
 *
 * <p>管理独立的 greeting.yml 配置文件，支持热重载。</p>
 *
 * @author Zm_Mmm
 */
public class GreetingConfigManager {

    private static final String CONFIG_FILE = "greeting.yml";

    private final KilacraftAI plugin;
    private File configFile;

    @Getter
    private boolean enabled;
    @Getter
    private int delayTicks;
    @Getter
    private String firstLoginPrompt;
    @Getter
    private String returningLoginPrompt;
    @Getter
    private int maxOwnOfflineEvents;
    @Getter
    private int maxFriendOfflineEvents;
    @Getter
    private int maxSummaryEvents;
    @Getter
    private String serverInfo;
    @Getter
    private int greetingCooldownMinutes;
    @Getter
    private boolean profileInjectionEnabled;

    public GreetingConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("greeting.enabled", true);
        this.delayTicks = yaml.getInt("greeting.delay_ticks", 100);
        this.firstLoginPrompt = yaml.getString("greeting.first_login_prompt", GreetingPromptBuilder.DEFAULT_FIRST_LOGIN_PROMPT);
        this.returningLoginPrompt = yaml.getString("greeting.returning_login_prompt", GreetingPromptBuilder.DEFAULT_RETURNING_PROMPT);
        this.maxOwnOfflineEvents = yaml.getInt("greeting.max_own_offline_events", 10);
        this.maxFriendOfflineEvents = yaml.getInt("greeting.max_friend_offline_events", 5);
        this.maxSummaryEvents = yaml.getInt("greeting.max_summary_events", 3);
        this.serverInfo = yaml.getString("greeting.server_info", "");
        this.greetingCooldownMinutes = yaml.getInt("greeting.greeting_cooldown_minutes", 0);
        this.profileInjectionEnabled = yaml.getBoolean("greeting.profile_injection_enabled", true);
    }

    /**
     * 热重载配置
     */
    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }
}
