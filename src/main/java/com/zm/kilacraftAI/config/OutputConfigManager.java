package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * AI 响应输出配置管理器
 *
 * <p>管理独立的 output.yml 配置文件，支持热重载。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
@Getter
public class OutputConfigManager {

    private static final String CONFIG_FILE = "output.yml";

    private KilacraftAI plugin;

    /**
     * 全局默认输出载体
     */
    private OutputChannelEnum defaultChannel;

    /**
     * "正在思考"提示消息的输出载体
     */
    private OutputChannelEnum thinkingChannel;

    /**
     * 场景级载体覆盖配置。volatile + 不可变快照发布：reload 时先构建新 Map 再整体替换引用，
     * 避免 clear+put 期间读线程命中空/半 Map 回退到 defaultChannel。
     */
    private volatile Map<OutputScenarioEnum, OutputChannelEnum> scenarioChannels = Collections.emptyMap();

    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private int bossBarDurationSeconds;

    private int titleStayTicks;
    private int titleFadeInTicks;
    private int titleFadeOutTicks;

    private int sidebarDurationSeconds;
    private int sidebarMaxLinesPerPage;
    private int sidebarMaxCharsPerLine;
    private int sidebarMaxCharsPerLineEn;

    private boolean streamEnabled;

    private boolean soundEnabled;
    private String soundName;
    private float soundVolume;
    private float soundPitch;

    /**
     * 初始化并加载配置
     */
    public OutputConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    /**
     * 从独立 YAML 文件加载配置
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        loadFromConfig(yaml);
    }

    /**
     * 通用加载逻辑
     */
    private void loadFromConfig(FileConfiguration config) {
        // 基础配置
        this.defaultChannel = parseChannel(config.getString("output.default_channel", "CHAT"));

        // thinking_channel：未配置或为空时使用 default_channel
        String thinkingChannelStr = config.getString("output.thinking_channel", "");
        if (thinkingChannelStr == null || thinkingChannelStr.isEmpty()) {
            this.thinkingChannel = this.defaultChannel;
        } else {
            this.thinkingChannel = parseChannel(thinkingChannelStr);
        }

        // 场景级配置：构建新 Map 后整体替换（原子发布），避免 reload 期间读到半更新状态
        Map<OutputScenarioEnum, OutputChannelEnum> built = new EnumMap<>(OutputScenarioEnum.class);
        for (OutputScenarioEnum scenario : OutputScenarioEnum.values()) {
            String key = "output.scenarios." + scenario.name().toLowerCase();
            String value = config.getString(key, "");
            if (!value.isEmpty()) {
                built.put(scenario, parseChannel(value));
            }
        }
        this.scenarioChannels = Collections.unmodifiableMap(built);

        // BossBar 配置
        this.bossBarColor = parseBarColor(config.getString("output.boss_bar.color", "PURPLE"));
        this.bossBarStyle = parseBarStyle(config.getString("output.boss_bar.style", "SOLID"));
        this.bossBarDurationSeconds = config.getInt("output.boss_bar.duration_seconds", 15);

        // Title 配置
        this.titleStayTicks = config.getInt("output.title.stay_ticks", 60);
        this.titleFadeInTicks = config.getInt("output.title.fade_in_ticks", 10);
        this.titleFadeOutTicks = config.getInt("output.title.fade_out_ticks", 10);

        // Scoreboard 配置
        this.sidebarDurationSeconds = config.getInt("output.sidebar.duration_seconds", 15);
        this.sidebarMaxLinesPerPage = config.getInt("output.sidebar.max_lines_per_page", 15);
        this.sidebarMaxCharsPerLine = config.getInt("output.sidebar.max_chars_per_line", 30);
        this.sidebarMaxCharsPerLineEn = config.getInt("output.sidebar.max_chars_per_line_en", 0);

        // 流式输出配置
        this.streamEnabled = config.getBoolean("output.stream.enabled", false);

        // 音效配置
        this.soundEnabled = config.getBoolean("output.sound.enabled", true);
        this.soundName = config.getString("output.sound.sound_name", "ENTITY_PLAYER_LEVELUP");
        this.soundVolume = (float) config.getDouble("output.sound.volume", 0.5);
        this.soundPitch = (float) config.getDouble("output.sound.pitch", 1.2);
    }

    /**
     * 热重载配置
     */
    public void reload() {
        if (plugin != null) {
            loadConfig();
        }
    }

    private OutputChannelEnum parseChannel(String value) {
        if (value == null || value.isEmpty()) {
            return OutputChannelEnum.CHAT;
        }
        try {
            return OutputChannelEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OutputChannelEnum.CHAT;
        }
    }

    private BarColor parseBarColor(String value) {
        try {
            return BarColor.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.PURPLE;
        }
    }

    private BarStyle parseBarStyle(String value) {
        try {
            return BarStyle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    /**
     * 获取指定场景的输出载体
     */
    public OutputChannelEnum getChannelForScenario(OutputScenarioEnum scenario) {
        return scenarioChannels.getOrDefault(scenario, defaultChannel);
    }

    /**
     * 获取语言感知的 Sidebar 每行最大字符数
     */
    public int getSidebarMaxCharsPerLine(boolean isChinese) {
        if (!isChinese && sidebarMaxCharsPerLineEn > 0) {
            return sidebarMaxCharsPerLineEn;
        }
        return sidebarMaxCharsPerLine;
    }
}
