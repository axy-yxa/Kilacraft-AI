package com.zm.kilacraftAI.manager;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * AI 回复音效管理器
 * 在 AI 开始回复时播放提示音效
 * 音效与输出内容同步播放，仅触发玩家听到
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public class SoundEffectManager {

    private final KilacraftAI plugin;

    /**
     * 是否启用音效
     */
    @Getter
    private boolean enabled;
    private Sound sound;
    private float volume;
    private float pitch;

    public SoundEffectManager(KilacraftAI plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载音效配置
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("output.sound.enabled", true);

        // 不启用则直接返回，不加载音效配置
        if (!enabled) {
            return;
        }

        String soundName = plugin.getConfig().getString("output.sound.sound_name", "ENTITY_PLAYER_LEVELUP");
        try {
            this.sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            PluginLogger.warn("音效管理", I18nService.tr("无效的音效枚举: {}，使用默认值 ENTITY_PLAYER_LEVELUP", soundName));
            this.sound = Sound.ENTITY_PLAYER_LEVELUP;
        }

        this.volume = (float) plugin.getConfig().getDouble("output.sound.volume", 0.5);
        this.pitch = (float) plugin.getConfig().getDouble("output.sound.pitch", 1.2);

        // 限制范围
        this.volume = Math.max(0.0f, Math.min(1.0f, this.volume));
        this.pitch = Math.max(0.5f, Math.min(2.0f, this.pitch));
    }

    /**
     * 播放 AI 回复音效
     *
     * <p>在 AI 开始回复时调用，与输出内容同步</p>
     * <p>线程安全：自动切换到主线程/区域线程执行</p>
     *
     * @param player 目标玩家
     */
    public void playResponseSound(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }

        // 线程安全：切换到同步线程
        if (FoliaCompat.isPrimaryThread()) {
            playSoundSync(player);
        } else {
            FoliaCompat.runTask(plugin, () -> playSoundSync(player));
        }
    }

    /**
     * 同步播放音效（必须在主线程/区域线程调用）
     */
    private void playSoundSync(Player player) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            PluginLogger.error("音效管理", "播放音效失败", e);
        }
    }

}
