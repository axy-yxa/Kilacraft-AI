package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 人格配置文件管理器
 *
 * <p>管理插件命令使用的人格配置文件（personalities.yml）</p>
 * <p>该文件与 config.yml 同级，存储不同人格的提示词配置</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-26
 */
public class PersonalitiesConfigManager {

    private final KilacraftAI plugin;
    private final File personalitiesFile;

    // 人格缓存（人格中文名 -> 提示词）
    private final Map<String, String> personalitiesCache;

    // 公共提示词（所有人格共享的基础提示词）
    private String commonPrompt;

    // 配置文件对象
    private FileConfiguration config;

    public PersonalitiesConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;

        // 根据当前语言选择配置文件（zh=personalities.yml, en=personalities_en.yml）
        String lang = plugin.getConfigManager().getLanguage();
        String fileName = "zh".equals(lang) ? "personalities.yml" : "personalities_" + lang + ".yml";
        this.personalitiesFile = new File(plugin.getDataFolder(), fileName);
        this.personalitiesCache = new HashMap<>();

        // 复制默认配置
        ConfigResourceUtil.saveDefaultResource(plugin, fileName, "人格配置");
        loadConfig();
    }

    /**
     * 加载配置文件（YAML 格式）
     */
    public void loadConfig() {
        try {
            // 先检查文件是否存在且可读
            if (!personalitiesFile.exists()) {
                PluginLogger.warn("人格配置", "人格配置文件不存在");
                return;
            }

            // 清空缓存，确保重新加载
            personalitiesCache.clear();

            // 关键修复：每次重新从文件加载，避免 Bukkit 缓存导致重载不生效
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(personalitiesFile);

            // 获取所有配置节
            ConfigurationSection section = config.getConfigurationSection("");
            if (section == null || section.getKeys(false).isEmpty()) {
                PluginLogger.warn("人格配置", "人格配置文件为空");
                return;
            }

            // 加载公共提示词
            commonPrompt = config.getString("common_prompt", "");

            // 加载所有的人格配置
            for (String key : section.getKeys(false)) {
                // 跳过 common_prompt 配置项
                if ("common_prompt".equals(key)) {
                    continue;
                }
                String prompt = section.getString(key);
                if (!key.trim().isEmpty() && prompt != null && !prompt.trim().isEmpty()) {
                    personalitiesCache.put(key, prompt);
                }
            }

            PluginLogger.info("人格配置", "人格配置加载完成，共 {} 个人格", personalitiesCache.size());
        } catch (Exception e) {
            PluginLogger.error("人格配置", I18nService.tr("加载人格配置文件失败：{}", personalitiesFile.getAbsolutePath()), e);
        }
    }

    /**
     * 获取指定人格的提示词
     *
     * @param personalityName 人格名称（中文）
     * @return 人格提示词（包含公共提示词 + 人格私有提示词），如果不存在则返回 null
     */
    public String getPersonalityPrompt(String personalityName) {
        String personalityPrompt = personalitiesCache.get(personalityName);
        if (personalityPrompt == null) {
            return null;
        }

        // 如果有公共提示词，则追加到人格提示词前面
        if (commonPrompt != null && !commonPrompt.isEmpty()) {
            return commonPrompt + "\n" + personalityPrompt;
        }

        return personalityPrompt;
    }

    /**
     * 检查人格是否存在
     *
     * @param personalityName 人格名称
     * @return true=存在，false=不存在
     */
    public boolean hasPersonality(String personalityName) {
        return personalitiesCache.containsKey(personalityName);
    }

    /**
     * 获取所有可用的人格名称列表
     *
     * @return 人格名称集合
     */
    public java.util.Set<String> getAllPersonalities() {
        return personalitiesCache.keySet();
    }

    /**
     * 重新加载配置文件
     */
    public void reload() {
        PluginLogger.info("人格配置", "正在重新加载人格配置...");
        loadConfig();
        PluginLogger.info("人格配置", "人格配置加载完成");
    }
}
