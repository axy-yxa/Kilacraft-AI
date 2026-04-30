package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.skills.bukkit.BukkitAPIConfigLoader;
import com.zm.kilacraftAI.skills.bukkit.BukkitAPIMetadata;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能配置管理器
 *
 * <p>负责加载和管理所有技能的配置文件</p>
 * <p>支持两种类型的技能配置：</p>
 * <ul>
 *     <li>传统技能配置（如 MarketQuerySkill）- 从 YAML 文件读取完整配置</li>
 *     <li>数据驱动技能（如 GenericBukkitAPI）- 从 apis.yml 读取元数据</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class SkillConfigManager {

    private final KilacraftAI plugin;
    private final File skillsFolder;

    /**
     * 传统技能配置缓存
     * key = packageName.skillName
     */
    private final Map<String, SkillConfig> skillConfigs;

    /**
     * Bukkit API 元数据列表
     */
    @Getter
    private final Map<String, BukkitAPIMetadata> bukkitApiMap;

    /**
     * Bukkit API 全局提示信息
     */
    @Getter
    private final List<String> bukkitApiGlobalHints;

    /**
     * Bukkit API 技能描述
     */
    @Getter
    private String bukkitApiSkillDescription;

    @Getter
    private static SkillConfigManager instance;

    public SkillConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.skillsFolder = new File(plugin.getDataFolder(), "skills");
        this.skillConfigs = new LinkedHashMap<>();
        this.bukkitApiMap = new ConcurrentHashMap<>();
        this.bukkitApiGlobalHints = new ArrayList<>();
        this.bukkitApiSkillDescription = "";
        instance = this;
    }

    /**
     * 加载所有技能配置
     */
    public void loadAllSkillConfigs() {
        // 确保 skills 目录存在
        if (!skillsFolder.exists()) {
            skillsFolder.mkdirs();
        }

        // 加载传统技能配置
        loadTraditionalSkillConfigs();

        // 加载 Bukkit API 元数据
        loadBukkitAPIs();

        PluginLogger.info("技能配置", "已加载 {} 个技能配置", skillConfigs.size());
    }

    /**
     * 加载传统技能配置
     */
    private void loadTraditionalSkillConfigs() {
        File[] packageFolders = skillsFolder.listFiles(File::isDirectory);
        if (packageFolders == null) {
            return;
        }

        String lang = plugin.getConfigManager().getLanguage();
        boolean isZh = "zh".equals(lang);

        // 确保当前语言版本的配置文件已从 JAR 拷贝到磁盘（解决语言切换后文件缺失问题）
        ensureLanguageConfigFiles(isZh, lang);

        for (File packageFolder : packageFolders) {
            String packageName = packageFolder.getName();

            // 列出所有 .yml 文件，排除 apis.yml 和其他语言的配置文件
            File[] configFiles = packageFolder.listFiles((dir, name) -> {
                if (!name.endsWith(".yml") || name.equalsIgnoreCase("apis.yml")) return false;
                // zh 模式：排除 _en.yml 等带语言后缀的文件
                // 非 zh 模式：只加载 _{lang}.yml 文件
                if (isZh) {
                    return !name.matches(".*_[a-z]{2}\\.yml$");
                } else {
                    return name.endsWith("_" + lang + ".yml");
                }
            });
            if (configFiles == null) {
                // 目录存在但无配置文件，配置文件将由具体 Skill 实例在构造时从 JAR 模板创建
                continue;
            }

            for (File configFile : configFiles) {
                String skillName = configFile.getName().replace(".yml", "");
                // 剥离语言后缀：AFKTaskSkill_en → AFKTaskSkill，确保缓存 key 不含语言后缀
                if (!isZh) {
                    skillName = skillName.replaceAll("_" + lang + "$", "");
                }
                loadSkillConfig(packageName, skillName, configFile);
            }
        }
    }

    /**
     * 确保当前语言版本的配置文件已从 JAR 拷贝到磁盘
     *
     * <p>解决热重载时语言切换后，磁盘上只有旧语言版本配置文件、新语言版本文件缺失的问题。</p>
     *
     * @param isZh 当前是否为中文模式
     * @param lang 当前语言代码
     */
    private void ensureLanguageConfigFiles(boolean isZh, String lang) {
        try {
            var resource = plugin.getClass().getClassLoader().getResource("skills");
            if (resource == null) return;

            java.net.URI uri = resource.toURI();
            try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(uri, java.util.Collections.emptyMap())) {
                java.nio.file.Path skillsPath = fs.getPath("skills");
                try (java.util.stream.Stream<java.nio.file.Path> dirs = java.nio.file.Files.list(skillsPath)) {
                    dirs.filter(java.nio.file.Files::isDirectory).forEach(pkgDir -> {
                        String pkgName = pkgDir.getFileName().toString();
                        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(pkgDir)) {
                            files.filter(java.nio.file.Files::isRegularFile).filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                                String fileName = p.getFileName().toString();
                                // 跳过 apis.yml（由 loadBukkitAPIs 单独处理）
                                if (fileName.equalsIgnoreCase("apis.yml")) return;

                                // 判断该文件是否属于当前语言
                                boolean isForCurrentLang;
                                if (isZh) {
                                    // zh 模式：不带语言后缀的文件（如 AFKTaskSkill.yml）
                                    isForCurrentLang = !fileName.matches(".*_[a-z]{2}\\.yml$");
                                } else {
                                    // 非 zh 模式：带 _{lang}.yml 后缀的文件（如 AFKTaskSkill_en.yml）
                                    isForCurrentLang = fileName.endsWith("_" + lang + ".yml");
                                }

                                if (isForCurrentLang) {
                                    ConfigResourceUtil.saveDefaultResource(plugin, "skills/" + pkgName + "/" + fileName, "技能配置");
                                }
                            });
                        } catch (Exception e) {
                            PluginLogger.warn("技能配置", I18nService.tr("扫描 JAR 包技能目录失败: skills/{}", pkgName));
                        }
                    });
                }
            }
        } catch (Exception e) {
            PluginLogger.warn("技能配置", I18nService.tr("扫描 JAR 包技能配置目录失败: {}", e.getMessage()));
        }
    }

    /**
     * 加载 Bukkit API 元数据（数据驱动技能）
     */
    private void loadBukkitAPIs() {
        File bukkitFolder = new File(skillsFolder, "bukkit");
        if (!bukkitFolder.exists()) {
            bukkitFolder.mkdirs();
        }

        // 根据当前语言选择 apis.yml 或 apis_en.yml
        String lang = plugin.getConfigManager().getLanguage();
        String apisResourceName = "zh".equals(lang) ? "apis.yml" : "apis_" + lang + ".yml";
        ConfigResourceUtil.saveDefaultResourceToDir(plugin, "skills/bukkit/" + apisResourceName, bukkitFolder, "技能配置");

        File apisFile = new File(bukkitFolder, apisResourceName);
        if (apisFile.exists()) {
            try {
                BukkitAPIConfigLoader loader = new BukkitAPIConfigLoader();
                List<BukkitAPIMetadata> loadedApis = loader.loadFromFile(apisFile);

                // 加载全局 hints
                List<String> globalHints = loader.loadGlobalHints(apisFile);
                bukkitApiGlobalHints.clear();
                bukkitApiGlobalHints.addAll(globalHints);

                // 加载全局技能描述
                String skillDescription = loader.loadSkillDescription(apisFile);
                bukkitApiSkillDescription = skillDescription != null ? skillDescription : "";

                // 转为 Map 存储，key 为 API ID
                for (BukkitAPIMetadata api : loadedApis) {
                    bukkitApiMap.put(api.getId(), api);
                }

                PluginLogger.info("技能配置", "已加载 {} 个 Bukkit API", loadedApis.size());
            } catch (Exception e) {
                PluginLogger.error("技能配置", I18nService.tr("加载 Bukkit API 配置失败：{}", apisFile.getPath()), e);
            }
        }
    }

    /**
     * 加载单个技能配置
     */
    private void loadSkillConfig(String packageName, String skillName, File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            // 提取自定义字段（非标准字段：description / action_descriptions / hints 之外的所有字符串值字段）
            Map<String, String> customFields = extractCustomFields(config);

            SkillConfig skillConfig = new SkillConfig(packageName, skillName, config.getString("description", ""), getActionDescriptions(config), getHints(config), customFields);

            String key = packageName + "." + skillName;
            skillConfigs.put(key, skillConfig);
        } catch (Exception e) {
            PluginLogger.error("技能配置", I18nService.tr("加载技能配置失败：{}", configFile.getPath()), e);
        }
    }

    /**
     * 提取自定义字段（排除标准字段 description / action_descriptions / hints）
     */
    private Map<String, String> extractCustomFields(FileConfiguration config) {
        Map<String, String> custom = new LinkedHashMap<>();
        Set<String> standardKeys = Set.of("description", "action_descriptions", "hints");

        for (String key : config.getKeys(false)) {
            if (standardKeys.contains(key)) continue;
            // 只缓存字符串值字段（跳过 ConfigurationSection）
            if (config.isString(key)) {
                String value = config.getString(key);
                if (value != null && !value.isEmpty()) {
                    custom.put(key, value);
                }
            }
        }
        return custom;
    }

    /**
     * 从配置文件获取动作描述 (Map 格式)
     */
    private Map<String, String> getActionDescriptions(FileConfiguration config) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        if (config.isConfigurationSection("action_descriptions")) {
            var section = config.getConfigurationSection("action_descriptions");
            for (String key : section.getKeys(false)) {
                descriptions.put(key, section.getString(key));
            }
        }

        return descriptions;
    }

    /**
     * 从配置文件获取提示信息 (List 格式)
     */
    private List<String> getHints(FileConfiguration config) {
        List<String> hints = new ArrayList<>();

        // 优先使用 getStringList，兼容性更好
        List<String> loadedHints = config.getStringList("hints");
        if (!loadedHints.isEmpty()) {
            hints = new ArrayList<>(loadedHints);
        }

        return hints;
    }

    /**
     * 保存默认技能配置文件 (如果不存在)
     */
    public void saveDefaultSkillConfig(String packageName, String skillName) {
        String lang = plugin.getConfigManager().getLanguage();
        String fileName = "zh".equals(lang) ? skillName + ".yml" : skillName + "_" + lang + ".yml";
        String resourcePath = "skills/" + packageName + "/" + fileName;
        ConfigResourceUtil.saveDefaultResource(plugin, resourcePath, "技能配置");
    }

    /**
     * 获取指定技能的配置
     */
    public SkillConfig getSkillConfig(String packageName, String skillName) {
        String key = packageName + "." + skillName;
        return skillConfigs.get(key);
    }

    /**
     * 动态加载单个技能配置（如果文件存在）
     * 用于在插件运行时按需加载配置
     *
     * @param packageName 包名
     * @param skillName   技能名
     * @return 加载后的配置，失败返回 null
     */
    public SkillConfig loadSingleSkillConfig(String packageName, String skillName) {
        String lang = plugin.getConfigManager().getLanguage();
        String fileName = "zh".equals(lang) ? skillName + ".yml" : skillName + "_" + lang + ".yml";
        File configFile = new File(skillsFolder, packageName + "/" + fileName);
        if (!configFile.exists()) {
            return null;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            SkillConfig skillConfig = new SkillConfig(packageName, skillName, config.getString("description", ""), getActionDescriptions(config), getHints(config), extractCustomFields(config));

            String key = packageName + "." + skillName;
            skillConfigs.put(key, skillConfig);
            return skillConfig;
        } catch (Exception e) {
            PluginLogger.error("技能配置", I18nService.tr("动态加载技能配置失败：{}", configFile.getPath()), e);
            return null;
        }
    }

    /**
     * 热重载所有技能配置
     */
    public void reloadAllConfigs() {
        skillConfigs.clear();
        bukkitApiMap.clear();
        bukkitApiGlobalHints.clear();

        loadAllSkillConfigs();

        PluginLogger.info("技能配置", "技能配置重载完成");
    }
}
