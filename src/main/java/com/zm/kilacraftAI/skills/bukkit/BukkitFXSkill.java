package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.knowledge.InternalEnumRegistry;
import com.zm.kilacraftAI.skills.framework.*;
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

    private static final String SKILL_NAME = "bukkit_fx";
    private static final String LOG_PREFIX = "音效粒子";
    private static final String ACTION_PLAY_SOUND = "play_sound";
    private static final String ACTION_SPAWN_PARTICLE = "spawn_particle";

    private final SkillConfigManager configManager;

    public BukkitFXSkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
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
    public String getRequiredPermission() {
        return PluginPermissionEnum.BUKKIT_FX.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
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
            PluginLoggerUtil.error(LOG_PREFIX, "执行音效/粒子效果失败", e);
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
        String soundName = SkillEntityHelper.getString(entities, "sound");
        if (soundName == null) {
            return SkillResult.needInfo(I18nService.tr("要播放什么音效？请告诉我音效名或中文描述（如：村民叫声、升级音）。"));
        }

        // 解析音效：精确匹配 → 内置注册表模糊匹配
        Sound sound = resolveSound(soundName);
        if (sound == null) {
            return SkillResult.failure(I18nService.tr("无效的音效枚举名称: {}", soundName));
        }

        float volume = (float) SkillEntityHelper.getDouble(entities, "volume", 1.0);
        float pitch = (float) SkillEntityHelper.getDouble(entities, "pitch", 1.0);

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
        String particleName = SkillEntityHelper.getString(entities, "particle");
        if (particleName == null) {
            return SkillResult.needInfo(I18nService.tr("要显示什么粒子？请告诉我粒子名或中文描述（如：爱心、烟花）。"));
        }

        // 解析粒子：精确匹配 → 内置注册表模糊匹配
        Particle particle = resolveParticle(particleName);
        if (particle == null) {
            return SkillResult.failure(I18nService.tr("无效的粒子枚举名称: {}", particleName));
        }

        int count = SkillEntityHelper.getIntClamped(entities, "count", 10, 1, 100);
        double offsetX = SkillEntityHelper.getDouble(entities, "offset_x", 0.5);
        double offsetY = SkillEntityHelper.getDouble(entities, "offset_y", 0.5);
        double offsetZ = SkillEntityHelper.getDouble(entities, "offset_z", 0.5);

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
}
