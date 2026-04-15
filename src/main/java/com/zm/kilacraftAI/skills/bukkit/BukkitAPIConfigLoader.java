package com.zm.kilacraftAI.skills.bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bukkit API 元数据配置加载器
 *
 * <p>从 YAML 文件加载 API 定义（支持热重载）</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-01
 */
public class BukkitAPIConfigLoader {

    /**
     * 从指定文件加载配置（支持热重载）
     *
     * @param file YAML 配置文件
     * @return API 元数据列表
     */
    public List<BukkitAPIMetadata> loadFromFile(File file) throws Exception {
        if (!file.exists()) {
            throw new FileNotFoundException("配置文件不存在：" + file.getPath());
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return parseAllSections(config);
    }
    
    /**
     * 获取全局提示信息
     *
     * @param file YAML 配置文件
     * @return 全局 hints 列表
     */
    public List<String> loadGlobalHints(File file) {
        if (!file.exists()) {
            return new ArrayList<>();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> hints = config.getStringList("hints");
        return new ArrayList<>(hints);
    }
    
    /**
     * 获取全局技能描述
     *
     * @param file YAML 配置文件
     * @return 技能描述字符串
     */
    public String loadSkillDescription(File file) {
        if (!file.exists()) {
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getString("skill_description");
    }

    /**
     * 解析所有配置节点
     */
    private List<BukkitAPIMetadata> parseAllSections(FileConfiguration config) {
        List<BukkitAPIMetadata> apis = new ArrayList<>();

        // 解析玩家相关 API
        ConfigurationSection playerSection = config.getConfigurationSection("player");
        if (playerSection != null) {
            apis.addAll(parseMetadata(playerSection, "player"));
        }

        // 解析世界相关 API
        ConfigurationSection worldSection = config.getConfigurationSection("world");
        if (worldSection != null) {
            apis.addAll(parseMetadata(worldSection, "world"));
        }

        // 解析服务器相关 API
        ConfigurationSection serverSection = config.getConfigurationSection("server");
        if (serverSection != null) {
            apis.addAll(parseMetadata(serverSection, "server"));
        }

        return apis;
    }

    /**
     * 解析配置节点为元数据列表
     */
    private List<BukkitAPIMetadata> parseMetadata(ConfigurationSection section, String category) {
        List<BukkitAPIMetadata> apis = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            ConfigurationSection apiSection = section.getConfigurationSection(key);
            if (apiSection == null) continue;

            BukkitAPIMetadata metadata = new BukkitAPIMetadata();

            // 基础信息
            metadata.setId(apiSection.getString("id", key));
            metadata.setDisplayName(apiSection.getString("display_name", key));
            metadata.setDescription(apiSection.getString("description", ""));

            // 调用信息
            metadata.setTargetType(apiSection.getString("target_type", "Player"));
            metadata.setMethodChain(apiSection.getStringList("method_chain"));

            // 其他属性
            metadata.setResultTemplate(apiSection.getString("result_template"));
            
            // 额外方法映射
            if (apiSection.isConfigurationSection("additional_methods")) {
                var additionalMethods = apiSection.getConfigurationSection("additional_methods");
                Map<String, String> methodsMap = new HashMap<>();
                for (String methodKey : additionalMethods.getKeys(false)) {
                    methodsMap.put(methodKey, additionalMethods.getString(methodKey));
                }
                metadata.setAdditionalMethods(methodsMap);
            }
            
            apis.add(metadata);
        }

        return apis;
    }
}
