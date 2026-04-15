package com.zm.kilacraftAI.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 意图分类配置管理器
 *
 * <p>管理意图分类器所需的全部配置：BM25 阈值、祈使句式、闲聊句式、普通对话关键词。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-13
 */
public class IntentKeywordConfigManager {

    @Getter
    private static IntentKeywordConfigManager instance;

    private final JavaPlugin plugin;
    private File configFile;

    /** BM25 评分阈值 */
    @Getter
    private double skillMatchThreshold;

    /** 祈使句式额外加分 */
    @Getter
    private double imperativeBonus;

    /** 祈使句式列表 */
    @Getter
    private List<String> imperativePatterns;

    /** 闲聊句式列表（匹配到时强制 NORMAL_CHAT） */
    @Getter
    private List<String> chatPatterns;

    /** 普通对话关键词（快速短路） */
    @Getter
    private List<String> normalChatKeywords;

    private IntentKeywordConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public static synchronized IntentKeywordConfigManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new IntentKeywordConfigManager(plugin);
        }
        return instance;
    }

    public void loadConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            configFile = new File(plugin.getDataFolder(), "intent_keywords.yml");

            if (!configFile.exists()) {
                saveDefaultConfig();
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            // BM25 阈值与加分
            skillMatchThreshold = config.getDouble("skill_match_threshold", 20.0);
            imperativeBonus = config.getDouble("imperative_bonus", 10.0);

            // 句式列表
            imperativePatterns = config.getStringList("imperative_patterns");
            chatPatterns = config.getStringList("chat_patterns");
            normalChatKeywords = config.getStringList("normal_chat.keywords");

            plugin.getLogger().info("意图分类配置加载完成（阈值=" + skillMatchThreshold + "）");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载意图分类配置失败", e);
            // 加载失败时设置安全默认值
            skillMatchThreshold = 20.0;
            imperativeBonus = 10.0;
            imperativePatterns = Collections.emptyList();
            chatPatterns = Collections.emptyList();
            normalChatKeywords = Collections.emptyList();
        }
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().info("意图分类配置重载完成！");
    }

    private void saveDefaultConfig() {
        try (InputStream inputStream = plugin.getResource("intent_keywords.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, configFile.toPath());
                plugin.getLogger().info("已创建默认 intent_keywords.yml 意图分类配置文件");
            } else {
                plugin.getLogger().warning("未找到默认的 intent_keywords.yml 资源文件");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存默认配置失败", e);
        }
    }
}
