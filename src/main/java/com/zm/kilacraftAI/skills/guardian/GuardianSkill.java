package com.zm.kilacraftAI.skills.guardian;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.Guardian;
import com.zm.kilacraftAI.service.guardian.GuardianManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 守护系统自然语言入口
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public class GuardianSkill implements Skill {

    private static final String SKILL_NAME = "guardian";
    private static final String LOG_PREFIX = "守护系统";

    private final SkillConfigManager configManager;

    public GuardianSkill() {
        SkillConfigManager cm = SkillConfigManager.getInstance();
        this.configManager = cm;
        if (cm != null && cm.getSkillConfig(this) == null) {
            cm.saveDefaultSkillConfig(this);
            cm.loadSingleSkillConfig(this);
        }
    }

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
        return (config != null && !config.getDescription().isEmpty()) ? config.getDescription() : null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null && !config.getHints().isEmpty()) ? new ArrayList<>(config.getHints()) : Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.GUARDIAN.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        GuardianManager gm = KilacraftAI.getInstance().getGuardianManager();
        return gm != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }
        if (!PluginPermissionEnum.GUARDIAN.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.GUARDIAN.getNode())));
        }
        GuardianManager gm = KilacraftAI.getInstance().getGuardianManager();
        if (gm == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("守护系统未初始化")));
        }
        // action 为 null 时回退空串，避免 switch 抛 NullPointerException
        String safeAction = action == null ? "" : action;
        UUID playerId = player.getUniqueId();
        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("守护动作请求：玩家={}, 动作={}", player.getName(), safeAction));
        return CompletableFuture.completedFuture(switch (safeAction) {
            case "enable" -> {
                Optional<List<String>> result = gm.enable(player);
                if (result.isEmpty()) {
                    yield SkillResult.failure(I18nService.tr("守护系统已全局禁用，无法开启"));
                }
                PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("守护已开启：玩家={}, monitor={}", player.getName(), result.get()));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("monitor_loaded", result.get());
                yield SkillResult.success(I18nService.tr("守护已开启，{} 个 monitor 就绪", result.get().size()), data);
            }
            case "disable" -> {
                gm.disable(playerId);
                PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("守护已关闭：玩家={}", player.getName()));
                yield SkillResult.success(I18nService.tr("守护已关闭"));
            }
            case "status" -> {
                boolean on = gm.isGuardianEnabled(playerId);
                Guardian g = gm.getGuardian(playerId);
                List<String> monitors = new ArrayList<>();
                if (g != null) {
                    for (var m : g.monitors()) {
                        monitors.add(m.displayName());
                    }
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("enabled", on);
                data.put("monitors", monitors);
                yield SkillResult.success(on ? I18nService.tr("守护已开启，活跃 monitor：{}", monitors.size()) : I18nService.tr("守护未开启"), data);
            }
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", safeAction));
        });
    }
}
