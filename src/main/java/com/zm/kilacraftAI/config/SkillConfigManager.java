package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.skills.bukkit.BukkitAPIMetadata;
import com.zm.kilacraftAI.skills.bukkit.BukkitAPIConfigLoader;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        plugin.getLogger().info("已加载 " + skillConfigs.size() + " 个技能配置");
    }

    /**
     * 加载传统技能配置
     */
    private void loadTraditionalSkillConfigs() {
        File[] packageFolders = skillsFolder.listFiles(File::isDirectory);
        if (packageFolders == null) {
            return;
        }

        for (File packageFolder : packageFolders) {
            // 跳过 bukkit 目录（单独处理）
            if (packageFolder.getName().equalsIgnoreCase("bukkit")) {
                continue;
            }

            String packageName = packageFolder.getName();

            File[] configFiles = packageFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (configFiles == null) {
                // 目录存在但无配置文件，配置文件将由具体 Skill 实例在构造时从 JAR 模板创建
                continue;
            }

            for (File configFile : configFiles) {
                String skillName = configFile.getName().replace(".yml", "");
                loadSkillConfig(packageName, skillName, configFile);
            }
        }
    }

    /**
     * 加载 Bukkit API 元数据（数据驱动技能）
     */
    private void loadBukkitAPIs() {
        File bukkitFolder = new File(skillsFolder, "bukkit");
        if (!bukkitFolder.exists()) {
            bukkitFolder.mkdirs();
            copyDefaultBukkitAPIs(bukkitFolder);
        }

        File apisFile = new File(bukkitFolder, "apis.yml");
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
                
                plugin.getLogger().info("已加载 " + loadedApis.size() + " 个 Bukkit API");
            } catch (Exception e) {
                plugin.getLogger().severe("加载 Bukkit API 配置失败：" + apisFile.getPath());
                e.printStackTrace();
            }
        }
    }

    /**
     * 从 JAR 包复制默认的 Bukkit API 配置
     */
    private void copyDefaultBukkitAPIs(File bukkitFolder) {
        try {
            File defaultApisFile = new File(bukkitFolder, "apis.yml");
            if (!defaultApisFile.exists()) {
                plugin.saveResource("skills/bukkit/apis.yml", false);
                plugin.getLogger().info("已创建默认 Bukkit API 技能配置文件");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法创建默认 Bukkit API 配置：" + e.getMessage());
        }
    }

    /**
     * 加载单个技能配置
     */
    private void loadSkillConfig(String packageName, String skillName, File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            SkillConfig skillConfig = new SkillConfig(packageName, skillName, 
                config.getString("description", ""), 
                getActionDescriptions(config), 
                getResponseMessages(config),
                getHints(config));

            String key = packageName + "." + skillName;
            skillConfigs.put(key, skillConfig);

            plugin.getLogger().fine("已加载技能配置：" + key);

        } catch (Exception e) {
            plugin.getLogger().severe("加载技能配置失败：" + configFile.getPath());
            e.printStackTrace();
        }
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
        File packageFolder = new File(skillsFolder, packageName);
        if (!packageFolder.exists()) {
            packageFolder.mkdirs();
        }

        File configFile = new File(packageFolder, skillName + ".yml");
        if (configFile.exists()) {
            return;
        }

        try {
            String resourcePath = "skills/" + packageName + "/" + skillName + ".yml";
            plugin.saveResource(resourcePath, false);
            plugin.getLogger().info("已创建默认 " + skillName + ".yml" + " 技能配置文件");
        } catch (Exception e) {
            plugin.getLogger().severe("保存技能配置失败：" + configFile.getPath());
            e.printStackTrace();
        }
    }

    /**
     * 从配置文件获取响应消息 (Map 格式)
     */
    private Map<String, String> getResponseMessages(FileConfiguration config) {
        Map<String, String> messages = new LinkedHashMap<>();
        if (config.isConfigurationSection("response_messages")) {
            var section = config.getConfigurationSection("response_messages");
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key));
            }
        }

        return messages;
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
        File configFile = new File(skillsFolder, packageName + "/" + skillName + ".yml");
        if (!configFile.exists()) {
            return null;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            
            SkillConfig skillConfig = new SkillConfig(packageName, skillName,
                config.getString("description", ""),
                getActionDescriptions(config),
                getResponseMessages(config),
                getHints(config));
            
            String key = packageName + "." + skillName;
            skillConfigs.put(key, skillConfig);
            
            plugin.getLogger().fine("已动态加载技能配置：" + key);
            return skillConfig;
            
        } catch (Exception e) {
            plugin.getLogger().severe("动态加载技能配置失败：" + configFile.getPath());
            e.printStackTrace();
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
            
        plugin.getLogger().info("技能配置重载完成！");
    }
}
