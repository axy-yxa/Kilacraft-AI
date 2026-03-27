package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
    
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

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
                plugin.getLogger().info("已创建人格配置文件：" + personalitiesFile.getName());
                
                // 写入示例配置
                writeExampleConfig();
            } catch (IOException e) {
                plugin.getLogger().severe("创建人格配置文件失败：" + e.getMessage());
            }
        }
    }

    /**
     * 写入示例配置内容（JSON 格式）
     */
    private void writeExampleConfig() {
        Map<String, String> exampleData = new HashMap<>();
        
        exampleData.put("严厉教师", "你是一位严厉的 Minecraft 教师，正在教导玩家 {player}。你对学生的要求很高，说话简洁直接，但会耐心解答问题。专注于教授游戏机制、红石电路和建筑技巧。");
        exampleData.put("冒险伙伴", "你是玩家 {player} 的忠实冒险伙伴，性格开朗幽默。你喜欢分享探险故事，提供战斗建议，推荐装备搭配，总是鼓励玩家勇敢探索。");
        exampleData.put("图书管理员", "你是一位博学的图书管理员，正在为冒险者 {player} 提供知识服务。你说话文雅，喜欢引用古籍，精通 Minecraft 的历史、生物特性、矿物分布和各种冷知识。");
        exampleData.put("奸商", "你是一个精明的 Minecraft 商人，正在和顾客 {player} 交谈。你说话圆滑，总想推销自己的商品，对经济系统和交易价格了如指掌，时不时会开个玩笑。");
        
        try {
            FileWriter writer = new FileWriter(personalitiesFile, StandardCharsets.UTF_8);
            GSON.toJson(exampleData, writer);
            writer.flush();
            writer.close();
            plugin.getLogger().info("已写入示例人格配置（JSON 格式）");
        } catch (IOException e) {
            plugin.getLogger().severe("写入示例配置失败：" + e.getMessage());
        }
    }

    /**
     * 加载配置文件（JSON 格式）
     */
    public void loadConfig() {
        try {
            // 先检查文件是否存在且可读
            if (!personalitiesFile.exists()) {
                plugin.getLogger().warning("人格配置文件不存在，将创建示例文件");
                writeExampleConfig();
            }
            
            // 使用 UTF-8 读取 JSON 文件
            FileReader reader = new FileReader(personalitiesFile, StandardCharsets.UTF_8);
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loadedMap = GSON.fromJson(reader, mapType);
            reader.close();
            
            if (loadedMap == null) {
                plugin.getLogger().warning("人格配置文件为空，将创建示例文件");
                writeExampleConfig();
                // 重新加载
                loadConfig();
                return;
            }
            
            personalitiesCache.clear();
            
            // 加载所有的人格配置
            for (Map.Entry<String, String> entry : loadedMap.entrySet()) {
                String key = entry.getKey();
                String prompt = entry.getValue();
                if (key != null && !key.trim().isEmpty() && 
                    prompt != null && !prompt.trim().isEmpty()) {
                    personalitiesCache.put(key, prompt);
                    plugin.getLogger().fine("已加载人格配置：" + key);
                }
            }
            
            plugin.getLogger().info("人格配置加载完成，共 " + personalitiesCache.size() + " 个人格");
        } catch (Exception e) {
            plugin.getLogger().severe("加载人格配置文件失败：" + e.getMessage());
            plugin.getLogger().severe("文件路径：" + personalitiesFile.getAbsolutePath());
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
     * @return 人格提示词，如果不存在则返回 null
     */
    public String getPersonalityPrompt(String personalityName) {
        return personalitiesCache.get(personalityName);
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
     * 获取配置文件路径（用于调试）
     * 
     * @return 配置文件路径
     */
    public String getConfigPath() {
        return personalitiesFile.getAbsolutePath();
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
