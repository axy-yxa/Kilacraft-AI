package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.knowledge.InternalEnumRegistry;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit 音效与粒子效果 Skill
 *
 * <p>播放音效或显示粒子效果(仅调用者听到/看到)</p>
 * <p>用于: 任务完成庆祝、警告提示、氛围营造</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public class BukkitFXSkill implements Skill {

    private static final String ACTION_PLAY_SOUND = "play_sound";
    private static final String ACTION_SPAWN_PARTICLE = "spawn_particle";

    private final SkillConfigManager configManager;

    public BukkitFXSkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("bukkit", "BukkitFXSkill") == null) {
            configManager.saveDefaultSkillConfig("bukkit", "BukkitFXSkill");
            configManager.loadSingleSkillConfig("bukkit", "BukkitFXSkill");
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("bukkit", "BukkitFXSkill");
    }

    @Override
    public String getName() {
        return "bukkit_fx";
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null && !config.getActionDescriptions().isEmpty()) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("无法获取玩家对象")));
        }

        // 权限检查
        if (!PluginPermissionEnum.BUKKIT_FX.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.BUKKIT_FX.getNode())));
        }

        Map<String, String> entities = context.getEntities();

        try {
            // 必须在主线程执行(涉及玩家客户端交互)
            if (FoliaCompat.isPrimaryThread()) {
                return CompletableFuture.completedFuture(executeSync(action, player, entities));
            } else {
                CompletableFuture<SkillResult> future = new CompletableFuture<>();
                FoliaCompat.runTask(KilacraftAI.getInstance(), () -> {
                    try {
                        SkillResult result = executeSync(action, player, entities);
                        future.complete(result);
                    } catch (Exception e) {
                        future.complete(SkillResult.failure(I18nService.tr("执行失败: {}", e.getMessage())));
                    }
                });
                return future;
            }
        } catch (Exception e) {
            PluginLogger.error("BukkitFX", "执行音效/粒子效果失败", e);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("执行失败: {}", e.getMessage())));
        }
    }

    /**
     * 同步执行音效/粒子效果(必须在主线程/区域线程)
     */
    private SkillResult executeSync(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case ACTION_PLAY_SOUND -> playSound(player, entities);
            case ACTION_SPAWN_PARTICLE -> spawnParticle(player, entities);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 播放音效
     */
    private SkillResult playSound(Player player, Map<String, String> entities) {
        String soundName = entities.get("sound");
        if (soundName == null || soundName.isEmpty()) {
            return SkillResult.failure(I18nService.tr("缺少参数: sound(音效枚举名称)"));
        }

        // 解析音效：精确匹配 → 内置注册表模糊匹配
        Sound sound = resolveSound(soundName);
        if (sound == null) {
            return SkillResult.failure(I18nService.tr("无效的音效枚举名称: {}", soundName));
        }

        float volume = parseFloat(entities.get("volume"), 1.0f);
        float pitch = parseFloat(entities.get("pitch"), 1.0f);

        volume = Math.max(0.0f, Math.min(1.0f, volume));
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));

        Location location = player.getLocation();
        player.playSound(location, sound, volume, pitch);

        String result = I18nService.tr("音效播放成功: sound={}, volume={}, pitch={}, location=({}, {}, {}, {})", sound.name(), String.format("%.1f", volume), String.format("%.1f", pitch), String.format("%.1f", location.getX()), String.format("%.1f", location.getY()), String.format("%.1f", location.getZ()), location.getWorld().getName());
        return SkillResult.success(result);
    }

    /**
     * 解析音效枚举：精确 → 内置注册表模糊匹配
     */
    private Sound resolveSound(String input) {
        // 1. 精确匹配
        try {
            return Sound.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        // 2. 内置注册表模糊匹配
        InternalEnumRegistry registry = InternalEnumRegistry.getInstance();
        if (registry != null) {
            String matched = registry.resolveSound(input);
            if (matched != null) {
                try {
                    return Sound.valueOf(matched);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * 显示粒子效果
     */
    private SkillResult spawnParticle(Player player, Map<String, String> entities) {
        String particleName = entities.get("particle");
        if (particleName == null || particleName.isEmpty()) {
            return SkillResult.failure(I18nService.tr("缺少参数: particle(粒子枚举名称)"));
        }

        // 解析粒子：精确匹配 → 内置注册表模糊匹配
        Particle particle = resolveParticle(particleName);
        if (particle == null) {
            return SkillResult.failure(I18nService.tr("无效的粒子枚举名称: {}", particleName));
        }

        int count = parseInt(entities.get("count"), 10);
        double offsetX = parseFloat(entities.get("offset_x"), 0.5f);
        double offsetY = parseFloat(entities.get("offset_y"), 0.5f);
        double offsetZ = parseFloat(entities.get("offset_z"), 0.5f);

        count = Math.max(1, Math.min(100, count));

        Location location = player.getLocation();
        player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ);

        String result = I18nService.tr("粒子效果显示成功: particle={}, count={}, offset=({}, {}, {}), location=({}, {}, {}, {})", particle.name(), count, String.format("%.1f", offsetX), String.format("%.1f", offsetY), String.format("%.1f", offsetZ), String.format("%.1f", location.getX()), String.format("%.1f", location.getY()), String.format("%.1f", location.getZ()), location.getWorld().getName());
        return SkillResult.success(result);
    }

    /**
     * 解析粒子枚举：精确 → 内置注册表模糊匹配
     */
    private Particle resolveParticle(String input) {
        try {
            return Particle.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        InternalEnumRegistry registry = InternalEnumRegistry.getInstance();
        if (registry != null) {
            String matched = registry.resolveParticle(input);
            if (matched != null) {
                try {
                    return Particle.valueOf(matched);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * 安全解析Float(带默认值)
     */
    private float parseFloat(String value, float defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全解析Integer(带默认值)
     */
    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
