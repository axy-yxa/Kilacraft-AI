package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 守护系统配置管理器。
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public class GuardianConfigManager {

    private static final String CONFIG_FILE = "behavior.yml";

    private static final String DEFAULT_SYSTEM_PROMPT_ZH = """
            你是玩家的 Minecraft 游戏守护 AI。你会在玩家非即时感知的场景下主动提醒。
            请用1-2句简短自然的话提醒玩家，语气亲切、像朋友提醒。不要用固定模板句式，根据情况变化措辞。
            只输出提醒内容本身，不加前缀标签，不做分析总结，不问玩家要不要帮忙。
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_EN = """
            You are the player's Minecraft guardian AI. You proactively alert the player about
            things they cannot perceive in real time. Keep your message to 1-2 short, natural sentences,
            like a friend giving a heads-up. Do not use template phrasing — vary your wording based on context.
            Output only the alert content itself: no labels, no analysis, no asking if the player wants help.
            """;

    private final KilacraftAI plugin;
    private File configFile;

    @Getter
    private volatile boolean enabled;
    @Getter
    private volatile long heartbeatIntervalTicks;
    /**
     * 挂机判定阈值（秒），玩家无操作超过此时长视为 AFK，守护暂停。
     */
    @Getter
    private volatile long afkThresholdSeconds = 300L;
    private volatile String guardianSystemPromptZh = DEFAULT_SYSTEM_PROMPT_ZH;
    private volatile String guardianSystemPromptEn = DEFAULT_SYSTEM_PROMPT_EN;

    public GuardianConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            PluginLoggerUtil.warn("守护系统", I18nService.tr("配置文件不存在: {}", CONFIG_FILE));
            this.guardianSystemPromptZh = DEFAULT_SYSTEM_PROMPT_ZH;
            this.guardianSystemPromptEn = DEFAULT_SYSTEM_PROMPT_EN;
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("guardian.settings.enabled", true);
        // 心跳间隔下限保护：过小会导致每 tick 采样风暴
        this.heartbeatIntervalTicks = Math.max(10L, yaml.getLong("guardian.settings.heartbeat_interval_ticks", 20L));
        this.afkThresholdSeconds = yaml.getLong("guardian.settings.afk_threshold_seconds", 300L);
        // 存双语言，getter 时实时选——语言切换即时生效，不依赖 reload 顺序
        this.guardianSystemPromptZh = yaml.getString("guardian.prompts.system_prompt", DEFAULT_SYSTEM_PROMPT_ZH);
        this.guardianSystemPromptEn = yaml.getString("guardian.prompts.system_prompt_en", DEFAULT_SYSTEM_PROMPT_EN);

        PluginLoggerUtil.info("守护系统", I18nService.tr("配置加载完成"));
    }

    /**
     * 按当前语言返回守护系统提示词（实时判定，语言切换即时生效）。
     */
    public String getGuardianSystemPrompt() {
        return I18nService.isZh() ? guardianSystemPromptZh : guardianSystemPromptEn;
    }

    public void reload() {
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
        loadConfig();
    }
}
