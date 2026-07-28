package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    private File personalitiesFile;

    // 人格缓存（人格中文名 -> 提示词）。
    // volatile + 快照替换：loadConfig 先构建完整新 Map，再原子发布，消除"清空再填充"的瞬时空窗。
    private volatile Map<String, String> personalitiesCache;

    // 公共提示词（所有人格共享的基础提示词）
    private String commonPrompt;

    // 配置文件对象
    private FileConfiguration config;

    public PersonalitiesConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.personalitiesCache = new HashMap<>();

        // 初始化语言相关的配置文件
        updateConfigFile();
        loadConfig();
    }

    /**
     * 根据当前语言更新配置文件路径，并拷贝对应语言的默认配置
     */
    private void updateConfigFile() {
        String lang = plugin.getI18nService().getLanguage();
        String fileName = "zh".equals(lang) ? "personalities.yml" : "personalities_" + lang + ".yml";
        this.personalitiesFile = new File(plugin.getDataFolder(), fileName);
        ConfigResourceUtil.saveDefaultResource(plugin, fileName);
    }

    /**
     * 加载配置文件（YAML 格式）
     */
    public void loadConfig() {
        try {
            // 先检查文件是否存在且可读
            if (!personalitiesFile.exists()) {
                PluginLoggerUtil.warn("人格配置", "人格配置文件不存在");
                return;
            }

            // 关键修复：每次重新从文件加载，避免 Bukkit 缓存导致重载不生效
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(personalitiesFile);

            // 获取所有配置节
            ConfigurationSection section = config.getConfigurationSection("");
            if (section == null || section.getKeys(false).isEmpty()) {
                PluginLoggerUtil.warn("人格配置", "人格配置文件为空");
                return;
            }

            // 先构建完整的新 Map 和新公共提示词，再原子替换 volatile 引用。
            // 消除"先 clear 再逐条 put"的瞬时空窗：reload 期间并发读者看到的是旧 Map 或新 Map，不会看到空/半填充状态。
            String newCommonPrompt = config.getString("common_prompt", "");
            Map<String, String> newCache = new LinkedHashMap<>();

            for (String key : section.getKeys(false)) {
                if ("common_prompt".equals(key)) {
                    continue;
                }
                String prompt = section.getString(key);
                if (!key.trim().isEmpty() && prompt != null && !prompt.trim().isEmpty()) {
                    newCache.put(key, prompt);
                }
            }

            // 原子发布
            this.commonPrompt = newCommonPrompt;
            this.personalitiesCache = newCache;

            PluginLoggerUtil.info("人格配置", "人格配置加载完成，共 {} 个人格", newCache.size());
        } catch (Exception e) {
            PluginLoggerUtil.error("人格配置", I18nService.tr("加载人格配置文件失败：{}", personalitiesFile.getAbsolutePath()), e);
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
    public Set<String> getAllPersonalities() {
        return personalitiesCache.keySet();
    }

    /**
     * 重新加载配置文件（语言变更时同步切换文件）
     */
    public void reload() {
        PluginLoggerUtil.info("人格配置", "正在重新加载人格配置...");
        updateConfigFile();
        loadConfig();
        PluginLoggerUtil.info("人格配置", "人格配置加载完成");
    }
}
