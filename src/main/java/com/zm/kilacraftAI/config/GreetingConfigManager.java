package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.service.greeting.GreetingPromptBuilder;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * AI 登录问候配置管理器
 *
 * <p>管理独立的 greeting.yml 配置文件，支持热重载和语言切换。</p>
 * <p>按当前语言选择配置文件：zh=greeting.yml，其他语言=greeting_{lang}.yml</p>
 *
 * @author Zm_Mmm
 */
public class GreetingConfigManager {

    private static final String CONFIG_FILE_ZH = "greeting.yml";

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
    }

    /**
     * 根据当前语言更新配置文件路径，并拷贝对应语言的默认配置
     */
    private void updateConfigFile(String lang) {
        String fileName = "zh".equals(lang) ? CONFIG_FILE_ZH : "greeting_" + lang + ".yml";
        this.configFile = new File(plugin.getDataFolder(), fileName);
        ConfigResourceUtil.saveDefaultResource(plugin, fileName);
    }

    public void loadConfig() {
        String lang = plugin.getConfigManager() != null ? plugin.getConfigManager().getLanguage() : "zh";
        loadConfig(lang);
    }

    /**
     * 带语言参数加载配置（由 ConfigManager 在知道语言后调用）
     */
    public void loadConfig(String language) {
        updateConfigFile(language);

        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("greeting.enabled", true);
        this.delayTicks = yaml.getInt("greeting.delay_ticks", 100);
        this.firstLoginPrompt = yaml.getString("greeting.first_login_prompt", GreetingPromptBuilder.getDefaultFirstLoginPrompt());
        this.returningLoginPrompt = yaml.getString("greeting.returning_login_prompt", GreetingPromptBuilder.getDefaultReturningPrompt());
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
        loadConfig();
    }
}
