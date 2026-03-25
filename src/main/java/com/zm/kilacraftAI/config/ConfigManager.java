package com.zm.kilacraftAI.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置管理
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:22:28
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    @Getter
    private String apiKey;
    @Getter
    private String apiUrl;
    @Getter
    private String model;
    @Getter
    private double temperature;
    @Getter
    private int maxTokens;
    @Getter
    private boolean enableChatCommand;
    @Getter
    private int cooldownSeconds;
    @Getter
    private boolean debugMode;
    @Getter
    private int maxHistory;
    @Getter
    private boolean enableTrigger;
    @Getter
    private List<String> triggerKeywords;
    @Getter
    private List<String> allowedWorlds;
    @Getter
    private List<String> bannedWorlds;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // 读取 API 配置
        this.apiKey = config.getString("api.key", "sk-afbe212f24ca4014bcb8f6a152904677");
        this.apiUrl = config.getString("api.url", "https://api.deepseek.com/v1/chat/completions");
        this.model = config.getString("api.model", "deepseek-chat");
        this.temperature = config.getDouble("api.temperature", 0.7);
        this.maxTokens = config.getInt("api.max_tokens", 1000);

        // 读取基础设置
        this.enableChatCommand = config.getBoolean("settings.enable_chat_command", true);
        this.cooldownSeconds = config.getInt("settings.cooldown_seconds", 5);

        // 读取高级设置
        this.debugMode = config.getBoolean("settings.debug_mode", false);
        this.maxHistory = config.getInt("settings.max_history", 10);
        this.enableTrigger = config.getBoolean("settings.enable_trigger", true);

        // 读取关键词列表（逗号分隔）
        String keywordsStr = config.getString("settings.trigger_keywords", "@kila,@ai,@zm");
        this.triggerKeywords = Arrays.asList(keywordsStr.split(","));

        // 读取世界列表
        this.allowedWorlds = config.getStringList("settings.allowed_worlds");
        if (this.allowedWorlds.isEmpty()) {
            this.allowedWorlds = new ArrayList<>();
        }

        this.bannedWorlds = config.getStringList("settings.banned_worlds");
        if (this.bannedWorlds.isEmpty()) {
            this.bannedWorlds = new ArrayList<>();
        }
    }
}