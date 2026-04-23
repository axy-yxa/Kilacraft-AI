package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.enums.OutputScenario;
import lombok.Getter;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 响应输出配置
 *
 * <p>封装所有输出载体相关的配置项。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
@Getter
public class OutputConfigManager {

    // ==================== 基础配置 ====================

    /**
     * 全局默认输出载体
     */
    private OutputChannel defaultChannel;

    /**
     * "正在思考"提示消息的输出载体
     * <p>默认为 defaultChannel，可独立配置</p>
     */
    private OutputChannel thinkingChannel;

    /**
     * 场景级载体覆盖配置
     * <p>Key: OutputScenario, Value: 自定义载体（为空则使用 defaultChannel）</p>
     */
    private final Map<OutputScenario, OutputChannel> scenarioChannels = new HashMap<>();

    // ==================== BossBar 配置 ====================

    /**
     * BossBar 颜色
     */
    private BarColor bossBarColor;

    /**
     * BossBar 样式
     */
    private BarStyle bossBarStyle;

    /**
     * BossBar 显示时长（秒），0=永久
     */
    private int bossBarDurationSeconds;

    // ==================== Title 配置 ====================

    /**
     * Title 停留时间（ticks）
     */
    private int titleStayTicks;

    /**
     * Title 淡入时间（ticks）
     */
    private int titleFadeInTicks;

    /**
     * Title 淡出时间（ticks）
     */
    private int titleFadeOutTicks;

    // ==================== Scoreboard 配置 ====================

    /**
     * Scoreboard Sidebar 显示时长（秒），0=永久
     */
    private int sidebarDurationSeconds;

    /**
     * Scoreboard Sidebar 每页最大行数
     */
    private int sidebarMaxLinesPerPage;

    /**
     * Scoreboard Sidebar 每行最大字符数
     */
    private int sidebarMaxCharsPerLine;

    /**
     * Scoreboard Sidebar 每行最大字符数（英文版）
     * <p>英文每个字符视觉宽度较窄，Sidebar 可容纳更多字符</p>
     */
    private int sidebarMaxCharsPerLineEn;

    // ==================== 流式输出配置 ====================

    /**
     * 是否启用流式输出
     */
    private boolean streamEnabled;

    /**
     * 从配置文件加载输出配置
     *
     * @param config 配置对象
     */
    public void load(FileConfiguration config) {
        // 基础配置
        this.defaultChannel = parseChannel(config.getString("output.default_channel", "CHAT"));

        // thinking_channel：未配置或为空时使用 default_channel
        String thinkingChannelStr = config.getString("output.thinking_channel", "");
        if (thinkingChannelStr == null || thinkingChannelStr.isEmpty()) {
            this.thinkingChannel = this.defaultChannel;
        } else {
            this.thinkingChannel = parseChannel(thinkingChannelStr);
        }

        // 场景级配置
        scenarioChannels.clear();
        for (OutputScenario scenario : OutputScenario.values()) {
            String key = "output.scenarios." + scenario.name().toLowerCase();
            String value = config.getString(key, "");
            if (!value.isEmpty()) {
                scenarioChannels.put(scenario, parseChannel(value));
            }
        }

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
        this.sidebarMaxCharsPerLine = config.getInt("output.sidebar.max_chars_per_line", 32);
        this.sidebarMaxCharsPerLineEn = config.getInt("output.sidebar.max_chars_per_line_en", 0);

        // 流式输出配置
        this.streamEnabled = config.getBoolean("output.stream.enabled", false);
    }

    /**
     * 解析输出载体字符串
     */
    private OutputChannel parseChannel(String value) {
        if (value == null || value.isEmpty()) {
            return OutputChannel.CHAT;
        }
        try {
            return OutputChannel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 默认回退
            return OutputChannel.CHAT;
        }
    }

    /**
     * 解析 BossBar 颜色
     */
    private BarColor parseBarColor(String value) {
        try {
            return BarColor.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.PURPLE;
        }
    }

    /**
     * 解析 BossBar 样式
     */
    private BarStyle parseBarStyle(String value) {
        try {
            return BarStyle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    /**
     * 获取指定场景的输出载体
     *
     * @param scenario 输出场景
     * @return 载体类型（场景未配置时返回 defaultChannel）
     */
    public OutputChannel getChannelForScenario(OutputScenario scenario) {
        return scenarioChannels.getOrDefault(scenario, defaultChannel);
    }

    /**
     * 获取语言感知的 Sidebar 每行最大字符数
     * <p>英文模式下使用 max_chars_per_line_en（如果配置了），否则回退到中文版</p>
     *
     * @param isChinese 是否为中文模式
     * @return 每行最大字符数
     */
    public int getSidebarMaxCharsPerLine(boolean isChinese) {
        if (!isChinese && sidebarMaxCharsPerLineEn > 0) {
            return sidebarMaxCharsPerLineEn;
        }
        return sidebarMaxCharsPerLine;
    }
}
