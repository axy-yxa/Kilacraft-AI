package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 玩家自定义监听系统配置管理器。
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public class WatchConfigManager {

    private static final String CONFIG_FILE = "behavior.yml";
    private static final long MIN_POLL_INTERVAL_TICKS = 20L;

    private final KilacraftAI plugin;

    @Getter
    private volatile boolean enabled = true;
    @Getter
    private volatile long pollIntervalTicks = 600L;
    @Getter
    private volatile int offlineGraceMinutes = 5;
    @Getter
    private volatile int maxPollingWatches = 3;
    @Getter
    private volatile int maxEventWatches = 5;
    @Getter
    private volatile int maxWatchesGlobal = 200;
    @Getter
    private volatile int triggerCooldownSeconds = 30;
    @Getter
    private volatile String triggerActionPrompt = DEFAULT_TRIGGER_ACTION_PROMPT;

    /**
     * 触发指令构造提示词兜底默认（纯静态 system，动态内容由调用方拼入 user 消息）。
     */
    private static final String DEFAULT_TRIGGER_ACTION_PROMPT = """
            你是这个 Minecraft 服务器的 AI 助手。玩家创建了一个条件触发提醒（订阅/监听），条件已满足、事件已发生。
            玩家在创建时表达了触发后想执行的动作意图。用户消息中会提供事件描述与玩家意图。
            请把玩家意图转换为【一条玩家现在可以直接说出的动作请求】：
            - 用玩家第一人称视角、简洁口语
            - 去除条件语境——条件已满足，不要出现"上线后/等X时/如果/到了"等条件词
            - 只输出这一条请求本身，不要解释、不要加引号、不要输出其他内容
            """;

    /**
     * 触发指令构造提示词英文兜底默认（非 zh 语言时配置缺失的回退，与 behavior.yml trigger_action_prompt_en 一致）。
     */
    private static final String DEFAULT_TRIGGER_ACTION_PROMPT_EN = """
            You are the AI assistant of this Minecraft server. A player created a condition-triggered reminder (subscription/watch), and the condition is now met — the event has occurred.
            The player expressed a follow-up action they want to perform when creating it. The user message provides the event description and the player's intent.
            Convert that intent into ONE action request the player can say right now:
            - Use the player's first-person perspective, concise spoken language
            - Remove the condition context — the condition is met; do not include condition words like "when/after/once/if"
            - Output only the request itself — no explanation, no quotes, nothing else
            """;

    public WatchConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = yaml.getBoolean("watch.enabled", true);
        long interval = yaml.getLong("watch.poll_interval_ticks", 600L);
        this.pollIntervalTicks = Math.max(MIN_POLL_INTERVAL_TICKS, interval);
        this.offlineGraceMinutes = Math.max(0, yaml.getInt("watch.offline_grace_minutes", 5));
        this.maxPollingWatches = Math.max(1, yaml.getInt("watch.max_polling_watches", 3));
        this.maxEventWatches = Math.max(1, yaml.getInt("watch.max_event_watches", 5));
        this.maxWatchesGlobal = Math.max(1, yaml.getInt("watch.max_watches_global", 200));
        this.triggerCooldownSeconds = Math.max(0, yaml.getInt("watch.trigger_cooldown_seconds", 30));
        // 按语言取键（对齐 GreetingConfigManager 的中英分键模式），回退默认按语言选中/英
        boolean isZh = plugin.getConfigManager() != null && "zh".equals(plugin.getConfigManager().getLanguage());
        this.triggerActionPrompt = yaml.getString(isZh ? "watch.trigger_action_prompt" : "watch.trigger_action_prompt_en", isZh ? DEFAULT_TRIGGER_ACTION_PROMPT : DEFAULT_TRIGGER_ACTION_PROMPT_EN);
    }

    public void reload() {
        loadConfig();
    }
}
