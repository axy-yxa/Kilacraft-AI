package com.zm.kilacraftAI.skills.command;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.BukkitCommandUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 命令执行技能
 *
 * <p>以玩家身份执行服务器命令，权限边界等于玩家自身的权限。</p>
 * <p>玩家没有的命令权限，AI 代执行也会被服务器拒绝。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class CommandSkill implements Skill {

    private final SkillConfigManager configManager;

    public CommandSkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("command", "CommandSkill") == null) {
            configManager.saveDefaultSkillConfig("command", "CommandSkill");
            configManager.loadSingleSkillConfig("command", "CommandSkill");
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("command", "CommandSkill");
    }

    @Override
    public String getName() {
        return "command";
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
        if (config != null && config.getActionDescriptions() != null) {
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
        return new ArrayList<>();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.COMMAND_EXECUTE.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        // 全局开关检查
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null || !plugin.getConfigManager().isCommandSkillEnabled()) {
            return false;
        }
        // 仅在线玩家可用
        return context.getPlayer() != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // isAvailable 已保证玩家在线 + 全局开关开启（executeSkillByIntent 执行前会实时调用 isAvailable）
        Player player = context.getPlayer();
        if (!PluginPermissionEnum.COMMAND_EXECUTE.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.COMMAND_EXECUTE.getNode())));
        }

        String action = context.getAction();
        if (action == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未指定命令执行动作")));
        }

        try {
            return switch (action) {
                case "execute_command" -> executeCommand(player, context);
                default ->
                        CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作：{}", action)));
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("命令执行失败：{}", e.getMessage())));
        }
    }

    /**
     * 执行命令
     *
     * <p>以玩家身份执行命令，通过 BukkitCommandUtil 确保在主线程执行。</p>
     */
    private CompletableFuture<SkillResult> executeCommand(Player player, SkillContext context) {
        String rawCommand = context.getEntity("command");
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("请提供要执行的命令"));
        }

        // 移除前导 /（用户可能包含也可能不包含）
        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        final String finalCommand = command;

        PluginLoggerUtil.debug("命令技能", "玩家 {} 通过 AI 执行命令: /{}", player.getName(), finalCommand);

        // 使用 dispatchAsync 获取执行结果
        return BukkitCommandUtil.dispatchAsync(player, finalCommand).thenApply(success -> {
            if (success) {
                return SkillResult.success(I18nService.tr("已执行命令: /{}", finalCommand));
            } else {
                return SkillResult.failure(I18nService.tr("命令执行失败，可能没有权限或命令不存在: /{}", finalCommand));
            }
        }).exceptionally(ex -> {
            PluginLoggerUtil.warn("命令技能", I18nService.tr("命令执行异常: /{} - {}", finalCommand, ex.getMessage()), ex);
            return SkillResult.failure(I18nService.tr("命令执行异常: /{}", finalCommand));
        });
    }
}
