package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.skills.config.SkillConfig;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能配置管理器
 *
 * <p>负责加载和管理所有技能的配置文件</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class SkillConfigManager {

    private final KilacraftAI plugin;
    private final File skillsFolder;
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
        // 确保 skills 文件夹存在
        if (!skillsFolder.exists()) {
            skillsFolder.mkdirs();
        }

        // 遍历 skills 文件夹下的所有子文件夹
        File[] packageFolders = skillsFolder.listFiles(File::isDirectory);
        if (packageFolders == null) {
            return;
        }

        for (File packageFolder : packageFolders) {
            String packageName = packageFolder.getName();

            // 查找该文件夹下的所有 .yml 文件
            File[] configFiles = packageFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (configFiles == null) {
                continue;
            }

            for (File configFile : configFiles) {
                String skillName = configFile.getName().replace(".yml", "");
                loadSkillConfig(packageName, skillName, configFile);
            }
        }

        plugin.getLogger().info("已加载 " + skillConfigs.size() + " 个技能配置");
    }

    /**
     * 加载单个技能配置
     */
    private void loadSkillConfig(String packageName, String skillName, File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            SkillConfig skillConfig = new SkillConfig(packageName, skillName, config.getString("description", ""), getActionDescriptions(config), getResponseMessages(config));

            String key = packageName + "." + skillName;
            skillConfigs.put(key, skillConfig);

            plugin.getLogger().fine("已加载技能配置：" + key);

        } catch (Exception e) {
            plugin.getLogger().severe("加载技能配置失败：" + configFile.getPath());
            e.printStackTrace();
        }
    }

    /**
     * 从配置文件获取动作描述（Map 格式）
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
     * 保存默认技能配置文件（如果不存在）
     */
    public void saveDefaultSkillConfig(String packageName, String skillName, String description, Map<String, String> actionDescriptions, Map<String, String> responseMessages) {
        // 创建文件夹
        File packageFolder = new File(skillsFolder, packageName);
        if (!packageFolder.exists()) {
            packageFolder.mkdirs();
        }

        // 创建配置文件
        File configFile = new File(packageFolder, skillName + ".yml");
        if (configFile.exists()) {
            return; // 文件已存在，不覆盖
        }

        try {
            // 从 resources 中复制模板文件
            String resourcePath = "skills/" + packageName + "/" + skillName + ".yml";
            plugin.saveResource(resourcePath, false);

            plugin.getLogger().info("已创建默认技能配置：" + configFile.getPath());

        } catch (Exception e) {
            plugin.getLogger().severe("保存技能配置失败：" + configFile.getPath());
            e.printStackTrace();
        }
    }

    /**
     * 从配置文件获取响应消息（Map 格式）
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
     *
     * @param packageName 包名称（如 globalmarketplus）
     * @param skillName   技能名称（如 MarketQuerySkill）
     * @return 技能配置，不存在则返回 null
     */
    public SkillConfig getSkillConfig(String packageName, String skillName) {
        String key = packageName + "." + skillName;
        return skillConfigs.get(key);
    }
}
