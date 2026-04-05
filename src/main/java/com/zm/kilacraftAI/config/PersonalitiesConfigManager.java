package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
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
        this.personalitiesFile = new File(plugin.getDataFolder(), "personalities.yml");
        this.personalitiesCache = new HashMap<>();

        // 确保配置文件存在
        createDefaultConfigIfNotExists();
        loadConfig();
    }

    /**
     * 创建默认配置文件（如果不存在）
     */
    private void createDefaultConfigIfNotExists() {
        if (!personalitiesFile.exists()) {
            try {
                // 创建父目录
                if (!personalitiesFile.getParentFile().exists()) {
                    personalitiesFile.getParentFile().mkdirs();
                }

                // 创建文件
                personalitiesFile.createNewFile();
                plugin.getLogger().info("已创建默认 " + personalitiesFile.getName() + " 人格配置文件");

                // 写入示例配置
                writeExampleConfig();
            } catch (IOException e) {
                plugin.getLogger().severe("创建人格配置文件失败：" + e.getMessage());
            }
        }
    }

    /**
     * 写入示例配置内容（YAML 格式）
     */
    private void writeExampleConfig() {
        try {
            // 使用 Bukkit 的配置 API 写入 YAML
            FileConfiguration newConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(personalitiesFile);

            // 公共提示词（所有人格共享的基础提示词）
            newConfig.set("common_prompt", "你是一个 Minecraft 游戏的 NPC，需要满足玩家的常见要求。");

            // 严厉教师
            newConfig.set("严厉教师", "你是一位严厉的 Minecraft 教师，正在教导玩家 {player}。\n" + "你对学生的要求很高，说话简洁直接，但会耐心解答问题。\n" + "专注于教授游戏机制、红石电路和建筑技巧。");

            // 冒险伙伴
            newConfig.set("冒险伙伴", "你是玩家 {player} 的忠实冒险伙伴，性格开朗幽默。\n" + "你喜欢分享探险故事，提供战斗建议，推荐装备搭配，总是鼓励玩家勇敢探索。");

            // 图书管理员
            newConfig.set("图书管理员", "你是一位博学的图书管理员，正在为冒险者 {player} 提供知识服务。\n" + "你说话文雅，喜欢引用古籍，精通 Minecraft 的历史、生物特性、矿物分布和各种冷知识。");

            // 奸商
            newConfig.set("奸商", "你是一个精明的 Minecraft 商人，正在和顾客 {player} 交谈。\n" + "你说话圆滑，总想推销自己的商品，对经济系统和交易价格了如指掌，时不时会开个玩笑。");

            newConfig.save(personalitiesFile);
            
            // 清空 config 引用，强制下次重新加载
            this.config = null;
        } catch (IOException e) {
            plugin.getLogger().severe("写入示例配置失败：" + e.getMessage());
        }
    }

    /**
     * 加载配置文件（YAML 格式）
     */
    public void loadConfig() {
        try {
            // 先检查文件是否存在且可读
            if (!personalitiesFile.exists()) {
                plugin.getLogger().warning("人格配置文件不存在，将创建示例文件");
                writeExampleConfig();
            }

            // 清空缓存，确保重新加载
            personalitiesCache.clear();

            // 关键修复：每次重新从文件加载，避免 Bukkit 缓存导致重载不生效
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(personalitiesFile);

            // 获取所有配置节
            ConfigurationSection section = config.getConfigurationSection("");
            if (section == null || section.getKeys(false).isEmpty()) {
                plugin.getLogger().warning("人格配置文件为空，将创建示例文件");
                writeExampleConfig();
                // 重新加载
                loadConfig();
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
                    plugin.getLogger().fine("已加载人格配置：" + key);
                }
            }

            plugin.getLogger().info("人格配置加载完成，共 " + personalitiesCache.size() + " 个人格");
        } catch (Exception e) {
            plugin.getLogger().severe("加载人格配置文件失败：" + e.getMessage());
            plugin.getLogger().severe("文件路径：" + personalitiesFile.getAbsolutePath());
            e.printStackTrace();
            // 直接重新生成配置文件
            writeExampleConfig();
            // 重新加载
            loadConfig();
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
        plugin.getLogger().info("正在重新加载人格配置...");
        loadConfig();
        plugin.getLogger().info("人格配置加载完成");
    }
}
