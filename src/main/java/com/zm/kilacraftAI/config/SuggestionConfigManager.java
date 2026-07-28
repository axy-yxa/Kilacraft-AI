package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 对话推荐系统配置管理器。独立 suggestion.yml 文件。
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionConfigManager {

    private static final String CONFIG_FILE = "suggestion.yml";

    private final KilacraftAI plugin;

    @Getter
    private volatile boolean enabled = true;
    @Getter
    private volatile int maxSuggestions = 2;
    @Getter
    private volatile int timeoutSeconds = 15;
    /**
     * 不生成推荐的输出场景名集合（OutputScenarioEnum.name()）。
     */
    private volatile Set<String> excludeScenarios = Set.of();
    /**
     * 从技能摘要中排除的 skill 名集合（存 skill.getName() 值；黑名单模式，新增 skill 自动纳入）。
     */
    private volatile Set<String> excludeSkills = Set.of();

    private volatile String displayTitle = "";
    private volatile String displaySeparator = "";
    private volatile String displayClickHint = "";
    private volatile String displayTitleEn = "";
    private volatile String displaySeparatorEn = "";
    private volatile String displayClickHintEn = "";

    private volatile String systemPrompt = "";
    private volatile String systemPromptEn = "";
    private volatile String userPromptTemplate = "";
    private volatile String userPromptTemplateEn = "";

    public SuggestionConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            PluginLoggerUtil.warn("对话推荐", I18nService.tr("配置文件不存在: {}", CONFIG_FILE));
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("enabled", true);
        // max_suggestions 钳位 1~5，防配置越界
        int max = yaml.getInt("max_suggestions", 2);
        this.maxSuggestions = Math.max(1, Math.min(5, max));
        this.timeoutSeconds = Math.max(1, yaml.getInt("timeout_seconds", 15));

        this.excludeScenarios = new HashSet<>(yaml.getStringList("exclude_scenarios"));
        this.excludeSkills = new HashSet<>(yaml.getStringList("exclude_skills"));

        this.displayTitle = yaml.getString("display.title", "§7你可能还想问：");
        this.displaySeparator = yaml.getString("display.separator", "§7 | ");
        this.displayClickHint = yaml.getString("display.click_hint", "点击发送此问题");
        this.displayTitleEn = yaml.getString("display.title_en", "§7You may also want to ask:");
        this.displaySeparatorEn = yaml.getString("display.separator_en", "§7 | ");
        this.displayClickHintEn = yaml.getString("display.click_hint_en", "Click to send this question");

        this.systemPrompt = yaml.getString("prompts.system_prompt", "");
        this.systemPromptEn = yaml.getString("prompts.system_prompt_en", "");
        this.userPromptTemplate = yaml.getString("prompts.user_prompt_template", "");
        this.userPromptTemplateEn = yaml.getString("prompts.user_prompt_template_en", "");

        PluginLoggerUtil.info("对话推荐", I18nService.tr("配置加载完成"));
    }

    public void reload() {
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
        loadConfig();
    }

    public boolean isScenarioEnabled(OutputScenarioEnum scenario) {
        return !excludeScenarios.contains(scenario.name());
    }

    public Set<String> getExcludeSkills() {
        return Collections.unmodifiableSet(excludeSkills);
    }

    public String getDisplayTitle() {
        return I18nService.isZh() ? displayTitle : displayTitleEn;
    }

    public String getDisplaySeparator() {
        return I18nService.isZh() ? displaySeparator : displaySeparatorEn;
    }

    public String getDisplayClickHint() {
        return I18nService.isZh() ? displayClickHint : displayClickHintEn;
    }

    public String getLocalizedSystemPrompt() {
        return I18nService.isZh() ? systemPrompt : systemPromptEn;
    }

    public String getLocalizedUserPromptTemplate() {
        return I18nService.isZh() ? userPromptTemplate : userPromptTemplateEn;
    }
}
