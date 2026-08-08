package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.greeting.GreetingPromptBuilder;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * AI 登录问候配置管理器
 *
 * <p>管理 behavior.yml 中 greeting 配置段，支持热重载。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public class GreetingConfigManager {

    private static final String CONFIG_FILE = "behavior.yml";

    private final KilacraftAI plugin;
    private File configFile;

    @Getter
    private volatile boolean enabled;
    @Getter
    private volatile int delayTicks;
    @Getter
    private volatile String firstLoginPrompt;
    @Getter
    private volatile String returningLoginPrompt;
    @Getter
    private volatile int maxOwnOfflineEvents;
    @Getter
    private volatile int maxFriendOfflineEvents;
    @Getter
    private volatile int maxSummaryEvents;
    @Getter
    private volatile String serverInfo;
    @Getter
    private volatile int greetingCooldownMinutes;
    @Getter
    private volatile boolean profileInjectionEnabled;

    public GreetingConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        String lang = plugin.getConfigManager() != null ? plugin.getConfigManager().getLanguage() : "zh";
        loadConfig(lang);
    }

    /**
     * 带语言参数加载配置（由 ConfigManager 在知道语言后调用）。
     * 始终读 behavior.yml 的 greeting 段，按语言选提示词段。
     */
    public void loadConfig(String language) {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);

        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        boolean isZh = "zh".equals(language);

        this.enabled = yaml.getBoolean("greeting.enabled", true);
        this.delayTicks = yaml.getInt("greeting.delay_ticks", 100);
        String firstKey = isZh ? "greeting.first_login_prompt" : "greeting.first_login_prompt_en";
        String returningKey = isZh ? "greeting.returning_login_prompt" : "greeting.returning_login_prompt_en";
        this.firstLoginPrompt = yaml.getString(firstKey, GreetingPromptBuilder.getDefaultFirstLoginPrompt());
        this.returningLoginPrompt = yaml.getString(returningKey, GreetingPromptBuilder.getDefaultReturningPrompt());
        this.maxOwnOfflineEvents = yaml.getInt("greeting.max_own_offline_events", 20);
        this.maxFriendOfflineEvents = yaml.getInt("greeting.max_friend_offline_events", 10);
        this.maxSummaryEvents = yaml.getInt("greeting.max_summary_events", 5);
        this.serverInfo = yaml.getString("greeting.server_info", "");
        this.greetingCooldownMinutes = yaml.getInt("greeting.greeting_cooldown_minutes", 30);
        this.profileInjectionEnabled = yaml.getBoolean("greeting.profile_injection_enabled", true);

        warnDeprecatedPlaceholders(this.firstLoginPrompt, this.returningLoginPrompt);
    }

    /**
     * 检测配置模板是否含已废弃的动态占位符并提示迁移。
     * <p>问候系统提示词静态化后，{@code {player}}、{@code {offline_duration}} 等动态占位符不再在 system 中替换；
     * 残留它们不会报错，但会作为字面量出现在 system 中，破坏跨玩家前缀缓存命中。检测到时 warn 一次提示迁移。</p>
     */
    private void warnDeprecatedPlaceholders(String firstPrompt, String returningPrompt) {
        String[] deprecated = {"{player}", "{offline_duration}", "{own_events_section}", "{friend_events_section}", "{online_friends_section}", "{last_session_highlights}", "{last_location}", "{summary_section}"};
        for (String p : deprecated) {
            if (firstPrompt != null && firstPrompt.contains(p)) {
                PluginLoggerUtil.warn("问候配置", I18nService.tr("first_login_prompt 含已废弃占位符 {}，请迁移到纯静态模板（动态数据已自动注入用户消息）", p));
            }
            if (returningPrompt != null && returningPrompt.contains(p)) {
                PluginLoggerUtil.warn("问候配置", I18nService.tr("returning_login_prompt 含已废弃占位符 {}，请迁移到纯静态模板（动态数据已自动注入用户消息）", p));
            }
        }
    }

    /**
     * 热重载配置
     */
    public void reload() {
        loadConfig();
    }
}
