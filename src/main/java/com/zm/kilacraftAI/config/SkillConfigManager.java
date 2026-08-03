package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 技能配置管理器
 *
 * <p>负责加载和管理所有技能的配置文件。所有技能（含 Bukkit 查询类）均走统一的
 * 标准配置加载路径：从 {@code skills/<package>/<ClassName>.yml} 读取 description /
 * action_descriptions / hints 三键。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class SkillConfigManager {

    private final KilacraftAI plugin;
    private final File skillsFolder;

    /**
     * 技能配置缓存
     * key = packageName.skillName
     */
    private final Map<String, SkillConfig> skillConfigs;

    @Getter
    private static SkillConfigManager instance;

    public SkillConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.skillsFolder = new File(plugin.getDataFolder(), "skills");
        this.skillConfigs = new LinkedHashMap<>();
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

        loadTraditionalSkillConfigs();

        PluginLoggerUtil.info("技能配置", "已加载 {} 个技能配置", skillConfigs.size());
    }

    /**
     * 加载传统技能配置
     */
    private void loadTraditionalSkillConfigs() {
        File[] packageFolders = skillsFolder.listFiles(File::isDirectory);
        if (packageFolders == null) {
            return;
        }

        String lang = plugin.getI18nService().getLanguage();
        boolean isZh = "zh".equals(lang);

        // 确保当前语言版本的配置文件已从 JAR 拷贝到磁盘（解决语言切换后文件缺失问题）
        ensureLanguageConfigFiles(isZh, lang);

        for (File packageFolder : packageFolders) {
            String packageName = packageFolder.getName();

            // 列出当前语言的 .yml 配置文件
            File[] configFiles = packageFolder.listFiles((dir, name) -> {
                if (!name.endsWith(".yml")) return false;
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
                // 剥离语言后缀：GenericBukkitAPISkill_en → GenericBukkitAPISkill，确保缓存 key 不含语言后缀
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

            URI uri = resource.toURI();
            try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path skillsPath = fs.getPath("skills");
                try (Stream<Path> dirs = Files.list(skillsPath)) {
                    dirs.filter(Files::isDirectory).forEach(pkgDir -> {
                        String pkgName = pkgDir.getFileName().toString();
                        try (Stream<Path> files = Files.list(pkgDir)) {
                            files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                                String fileName = p.getFileName().toString();

                                // 判断该文件是否属于当前语言
                                boolean isForCurrentLang;
                                if (isZh) {
                                    // zh 模式：不带语言后缀的文件（如 BukkitStatsSkill.yml）
                                    isForCurrentLang = !fileName.matches(".*_[a-z]{2}\\.yml$");
                                } else {
                                    // 非 zh 模式：带 _{lang}.yml 后缀的文件（如 BukkitStatsSkill_en.yml）
                                    isForCurrentLang = fileName.endsWith("_" + lang + ".yml");
                                }

                                if (isForCurrentLang) {
                                    ConfigResourceUtil.saveDefaultResource(plugin, "skills/" + pkgName + "/" + fileName);
                                }
                            });
                        } catch (Exception e) {
                            PluginLoggerUtil.warn("技能配置", "扫描 JAR 包技能目录失败: skills/{}", pkgName);
                        }
                    });
                }
            }
        } catch (Exception e) {
            PluginLoggerUtil.warn("技能配置", "扫描 JAR 包技能配置目录失败: {}", e.getMessage());
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
            PluginLoggerUtil.error("技能配置", I18nService.tr("加载技能配置失败：{}", configFile.getPath()), e);
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
     * 从 Skill 实例派生配置目录名（包名叶子段）。
     * 与 {@link #resolveConfigName(Skill)} 共同定位资源路径 skills/&lt;package&gt;/&lt;ClassName&gt;.yml。
     */
    private static String resolvePackage(Skill skill) {
        Package pkg = skill.getClass().getPackage();
        String full = pkg != null ? pkg.getName() : "";
        int idx = full.lastIndexOf('.');
        return idx >= 0 ? full.substring(idx + 1) : full;
    }

    /**
     * 从 Skill 实例派生配置文件名（类名，去掉语言后缀前的基名）。
     */
    private static String resolveConfigName(Skill skill) {
        return skill.getClass().getSimpleName();
    }

    /**
     * 保存默认技能配置文件（从 JAR 释放到磁盘，如果不存在）。
     * 资源路径由 Skill 实例的包名叶子段 + 类名派生：skills/&lt;package&gt;/&lt;ClassName&gt;[_lang].yml。
     */
    public void saveDefaultSkillConfig(Skill skill) {
        String packageName = resolvePackage(skill);
        String skillName = resolveConfigName(skill);
        String lang = plugin.getI18nService().getLanguage();
        String fileName = "zh".equals(lang) ? skillName + ".yml" : skillName + "_" + lang + ".yml";
        String resourcePath = "skills/" + packageName + "/" + fileName;
        ConfigResourceUtil.saveDefaultResource(plugin, resourcePath);
    }

    /**
     * 获取指定技能的配置（缓存查找，key = package.ClassName）。
     */
    public SkillConfig getSkillConfig(Skill skill) {
        String key = resolvePackage(skill) + "." + resolveConfigName(skill);
        return skillConfigs.get(key);
    }

    /**
     * 动态加载单个技能配置（如果文件存在），加载后存入缓存。
     * 用于在插件运行时按需加载配置。
     *
     * @return 加载后的配置，失败返回 null
     */
    public SkillConfig loadSingleSkillConfig(Skill skill) {
        String packageName = resolvePackage(skill);
        String skillName = resolveConfigName(skill);
        String lang = plugin.getI18nService().getLanguage();
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
            PluginLoggerUtil.error("技能配置", I18nService.tr("动态加载技能配置失败：{}", configFile.getPath()), e);
            return null;
        }
    }

    /**
     * 热重载所有技能配置
     */
    public void reloadAllConfigs() {
        skillConfigs.clear();

        loadAllSkillConfigs();

        PluginLoggerUtil.info("技能配置", "技能配置重载完成");
    }
}
