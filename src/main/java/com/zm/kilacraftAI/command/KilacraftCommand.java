package com.zm.kilacraftAI.command;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.command.impl.*;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 命令处理
 *
 * @author Zm_Mmm
 * @since 2026-03-24
 */
public class KilacraftCommand implements CommandExecutor {

    private final KilacraftAI plugin;
    private final LanguageManager languageManager;

    public KilacraftCommand(KilacraftAI plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // 缓存配置管理器引用
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length == 0) {
            sendHelpMessage(sender, configManager.isEnableChatCommand());
            return true;
        }

        // 子命令处理
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> ReloadCommand.handle(plugin, sender, args);
            case "clear" -> ClearCommand.handle(plugin, sender, args);
            case "chat" -> ChatCommand.handle(plugin, sender, args, configManager);
            case "knowledge" -> KnowledgeCommand.handle(plugin, sender, args);
            case "plugins" -> PluginsCommand.handle(plugin, sender, args);
            case "personalities" -> PersonalitiesCommand.handle(plugin, sender, args);
            case "suggestion" -> SuggestionCommand.handle(plugin, sender, args);
            case "tasks" -> TasksCommand.handle(plugin, sender, args);
            case "profile" -> ProfileCommand.handle(plugin, sender, args);
            case "notify" -> NotifyCommand.handle(plugin, sender, args);
            case "usage" -> UsageCommand.handle(plugin, sender, args);
            case "history" -> HistoryCommand.handle(plugin, sender, args);
            case "memory" -> MemoryCommand.handle(plugin, sender, args);
            case "skills" -> SkillsCommand.handle(plugin, sender, args);
            case "run" -> RunCommand.handle(plugin, sender, args);
            case "doctor" -> DoctorCommand.handle(plugin, sender, args);
            case "cache" -> CacheCommand.handle(plugin, sender, args);
            case "about" -> AboutCommand.handle(plugin, sender, args);
            default -> NormalChatCommand.handle(plugin, sender, args);
        }
        return true;
    }

    /**
     * 发送帮助消息
     */
    private void sendHelpMessage(CommandSender sender, boolean chatCommandEnabled) {
        // 标题 + 基础用法
        for (int i = 0; i < languageManager.getHelpMessages().size(); i++) {
            if (i == 2 && !chatCommandEnabled) continue;
            sender.sendMessage(languageManager.getHelpMessages().get(i));
        }
        sender.sendMessage("");

        // 重载配置（RELOAD 权限）
        if (PluginPermissionEnum.RELOAD.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpReload());
            sender.sendMessage("");
        }

        // 清除历史
        if (PluginPermissionEnum.CLEAR_SELF.hasPermission(sender))
            sender.sendMessage(languageManager.getHelpClearSelf());
        if (PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender))
            sender.sendMessage(languageManager.getHelpClearOther());
        if (PluginPermissionEnum.CLEAR_SELF.hasPermission(sender) || PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender))
            sender.sendMessage("");

        // 知识库 / 人格
        if (PluginPermissionEnum.KNOWLEDGE.hasPermission(sender))
            sender.sendMessage(languageManager.getHelpKnowledge());
        if (PluginPermissionEnum.PERSONALITIES.hasPermission(sender))
            sender.sendMessage(languageManager.getHelpPersonalities());
        if (PluginPermissionEnum.KNOWLEDGE.hasPermission(sender) || PluginPermissionEnum.PERSONALITIES.hasPermission(sender))
            sender.sendMessage("");

        // 性能采样 + 通知
        if (PluginPermissionEnum.ADMIN_HEALTH.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpProfile());
            sender.sendMessage(languageManager.getHelpProfileSubcommands());
            sender.sendMessage(languageManager.getHelpNotify());
            sender.sendMessage("");
        }

        // 查询命令
        if (PluginPermissionEnum.QUERY_SELF.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpUsage());
            sender.sendMessage(languageManager.getHelpHistory());
            sender.sendMessage(languageManager.getHelpMemory());
            sender.sendMessage("");
        }

        // 技能命令
        sender.sendMessage(languageManager.getHelpSkills());
        sender.sendMessage(languageManager.getHelpRun());
        // 对话推荐（全体可用）
        sender.sendMessage(languageManager.getHelpSuggestion());
        sender.sendMessage("");

        // 管理员信息
        if (PluginPermissionEnum.ADMIN_INFO.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpDoctor());
            sender.sendMessage(languageManager.getHelpAbout());
            sender.sendMessage("");
        }

        // 定时任务
        if (PluginPermissionEnum.TASKS.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpTasks());
            sender.sendMessage("");
        }

        // 大模型缓存统计
        if (PluginPermissionEnum.ADMIN_CACHE.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpCache());
            sender.sendMessage(languageManager.getHelpCacheReset());
            sender.sendMessage("");
        }
    }


}
