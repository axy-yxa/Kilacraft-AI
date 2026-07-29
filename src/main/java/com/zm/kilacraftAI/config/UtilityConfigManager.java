package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 工具通知提示词配置管理器。配置段位于 behavior.yml 的 utility.prompts.* 下。
 *
 * <p>供 UtilitySkill 的 notify_player / broadcast_message action 读取独立 LLM 调用提示词。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-29
 */
public class UtilityConfigManager {

    private static final String CONFIG_FILE = "behavior.yml";

    private final KilacraftAI plugin;

    private volatile String notifySystemPrompt = "";
    private volatile String notifySystemPromptEn = "";
    private volatile String notifyUserPrompt = "";
    private volatile String notifyUserPromptEn = "";
    private volatile String broadcastSystemPrompt = "";
    private volatile String broadcastSystemPromptEn = "";
    private volatile String broadcastUserPrompt = "";
    private volatile String broadcastUserPromptEn = "";

    public UtilityConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.notifySystemPrompt = yaml.getString("utility.prompts.notify_system_prompt", "");
        this.notifySystemPromptEn = yaml.getString("utility.prompts.notify_system_prompt_en", "");
        this.notifyUserPrompt = yaml.getString("utility.prompts.notify_user_prompt", "");
        this.notifyUserPromptEn = yaml.getString("utility.prompts.notify_user_prompt_en", "");
        this.broadcastSystemPrompt = yaml.getString("utility.prompts.broadcast_system_prompt", "");
        this.broadcastSystemPromptEn = yaml.getString("utility.prompts.broadcast_system_prompt_en", "");
        this.broadcastUserPrompt = yaml.getString("utility.prompts.broadcast_user_prompt", "");
        this.broadcastUserPromptEn = yaml.getString("utility.prompts.broadcast_user_prompt_en", "");
    }

    public void reload() {
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
        loadConfig();
    }

    public String getNotifySystemPrompt() {
        return I18nService.isZh() ? notifySystemPrompt : notifySystemPromptEn;
    }

    public String getNotifyUserPrompt() {
        return I18nService.isZh() ? notifyUserPrompt : notifyUserPromptEn;
    }

    public String getBroadcastSystemPrompt() {
        return I18nService.isZh() ? broadcastSystemPrompt : broadcastSystemPromptEn;
    }

    public String getBroadcastUserPrompt() {
        return I18nService.isZh() ? broadcastUserPrompt : broadcastUserPromptEn;
    }
}
