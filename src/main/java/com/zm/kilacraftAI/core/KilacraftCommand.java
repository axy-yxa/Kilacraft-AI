package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.event.AIResponseReadyEvent;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.handler.impl.PluginCommandResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 命令处理
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:51:48
 */
public class KilacraftCommand implements CommandExecutor {

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;
    private final LanguageManager languageManager;
    private final AIRequestHandler aiRequestHandler;

    public KilacraftCommand(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
        this.aiRequestHandler = new AIRequestHandler(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // 缓存配置管理器引用，避免重复调用
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length == 0) {
            sendHelpMessage(sender, configManager.isEnableChatCommand());
            return true;
        }

        // 子命令处理
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "reload" -> handleReloadCommand(sender);
            case "clear" -> handleClearCommand(sender, args);
            case "chat" -> handleChatCommand(sender, configManager);
            case "knowledge" -> handleKnowledgeCommand(sender, args);
            case "plugins" -> handlePluginsCommand(sender, args);
            case "personalities" -> handlePersonalitiesCommand(sender, args);
            default ->
                // 普通消息发送命令（无需权限检查）
                    handleNormalMessageCommand(sender, args);
        };
    }

    /**
     * 发送帮助消息
     */
    private void sendHelpMessage(CommandSender sender, boolean chatCommandEnabled) {
        // 发送基础帮助消息
        for (int i = 0; i < languageManager.getHelpMessages().size(); i++) {
            // 跳过 chat 命令提示如果功能被禁用
            if (i == 2 && !chatCommandEnabled) {
                continue;
            }
            sender.sendMessage(languageManager.getHelpMessages().get(i));
        }

        // 根据权限显示清除历史提示
        if (PluginPermissionEnum.CLEAR_SELF.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpClearSelf());
        }
        if (PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpClearOther());
        }
    }

    /**
     * 处理 reload 命令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!PluginPermissionEnum.RELOAD.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionReload());
            return true;
        }

        try {
            plugin.getConfigManager().loadConfig();
            plugin.getLanguageManager().loadConfig();

            // 热重载技能配置（包括 Bukkit API 元数据）
            if (plugin.getSkillConfigManager() != null) {
                plugin.getSkillConfigManager().reloadAllConfigs();
                sender.sendMessage("§a已重新加载技能配置文件");
            }

            // LLM 提供商配置的刷新已经在 ConfigManager.loadConfig() 中通过 refreshLLMConfigCache() 自动执行
            // 此处不再重复调用，避免连接池被重复关闭和重建

            sender.sendMessage(languageManager.getCommandReloadSuccess());
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogConfigReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandReloadFailure() + e.getMessage());
            plugin.getLogger().severe(languageManager.getLogAiRequestError() + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 clear 命令
     *
     * <p>支持清除自己或指定玩家的历史记录</p>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handleClearCommand(CommandSender sender, String[] args) {
        // 如果提供了玩家名称参数，清除指定玩家的历史（需要 kilacraft.clear.other 权限）
        if (args.length >= 2) {
            if (!PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
                sender.sendMessage(languageManager.getPermissionClearOther());
                return true;
            }

            String targetPlayerName = args[1];
            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
            UUID targetPlayerId;

            if (targetPlayer != null) {
                // 玩家在线，使用其 UUID
                targetPlayerId = targetPlayer.getUniqueId();
                targetPlayerName = targetPlayer.getName();
            } else {
                // 玩家不在线，尝试从离线数据获取 UUID
                targetPlayerId = plugin.getServer().getOfflinePlayer(targetPlayerName).getUniqueId();
            }

            // 清除该玩家的所有历史记录（包括普通和插件命令）
            plugin.getConversationManager().clearAllHistory(targetPlayerId);
            sender.sendMessage(languageManager.replacePlaceholders(languageManager.getCommandClearOtherSuccess(), "player", targetPlayerName));
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogClearOtherLogged(), "sender", getSenderName(sender), "player", targetPlayerName));
            return true;
        }

        // 没有参数，清除自己的历史记录（需要 kilacraft.clear.self 权限）
        if (!PluginPermissionEnum.CLEAR_SELF.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionClearSelf());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.getCommandClearConsoleHint());
            return true;
        }

        UUID playerId = player.getUniqueId();
        // 清除所有历史记录（包括普通和插件命令）
        plugin.getConversationManager().clearAllHistory(playerId);
        player.sendMessage(languageManager.getCommandClearSelfSuccess());
        plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogClearSelfLogged(), "player", player.getName()));
        return true;
    }

    /**
     * 处理 chat 命令
     */
    private boolean handleChatCommand(CommandSender sender, ConfigManager configManager) {
        if (!configManager.isEnableChatCommand()) {
            sender.sendMessage(languageManager.getFeatureChatModeDisabled());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.getFeatureChatModePlayerOnly());
            return true;
        }

        // 切换对话模式状态
        UUID playerId = player.getUniqueId();
        ConversationManager convManager = plugin.getConversationManager();
        boolean inChatMode = convManager.isInChatMode(playerId);
        convManager.setChatMode(playerId, !inChatMode);

        if (!inChatMode) {
            player.sendMessage(languageManager.getFeatureChatModeEnter());
            player.sendMessage(languageManager.getFeatureChatModeEnterSubtitle());
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogChatModeEntered(), "player", player.getName()));
        } else {
            player.sendMessage(languageManager.getFeatureChatModeExit());
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogChatModeExited(), "player", player.getName()));
        }
        return true;
    }

    /**
     * 处理 knowledge 命令
     */
    private boolean handleKnowledgeCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!PluginPermissionEnum.KNOWLEDGE.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionKnowledge());
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage(languageManager.getHelpKnowledge());
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handleKnowledgeReloadCommand(sender);
        } else {
            sender.sendMessage(languageManager.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(languageManager.getCommandAvailableSubcommands());
            return true;
        }
    }

    /**
     * 处理 knowledge reload 命令
     */
    private boolean handleKnowledgeReloadCommand(CommandSender sender) {
        try {
            plugin.getKnowledgeBase().reload();
            sender.sendMessage(languageManager.getCommandKnowledgeReloadSuccess());
            sender.sendMessage("§7" + plugin.getKnowledgeBase().getStatistics());
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogKnowledgeReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandKnowledgeReloadFailure() + e.getMessage());
            plugin.getLogger().severe(languageManager.getLogAiRequestError() + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 personalities 命令（人格配置管理）
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handlePersonalitiesCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!PluginPermissionEnum.PERSONALITIES.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionPersonalities());
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage(languageManager.getHelpPersonalities());
            sender.sendMessage(languageManager.getHelpPersonalitiesSubcommands());
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handlePersonalitiesReloadCommand(sender);
        } else {
            sender.sendMessage(languageManager.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(languageManager.getCommandAvailableSubcommands());
            return true;
        }
    }

    /**
     * 处理 personalities reload 命令
     *
     * @param sender 命令发送者
     * @return 执行结果
     */
    private boolean handlePersonalitiesReloadCommand(CommandSender sender) {
        try {
            plugin.getPersonalitiesConfigManager().reload();
            sender.sendMessage(languageManager.getCommandPersonalitiesReloadSuccess());
            sender.sendMessage("§7当前共加载 " + plugin.getPersonalitiesConfigManager().getAllPersonalities().size() + " 个人格");
            plugin.getLogger().info(languageManager.replacePlaceholders(languageManager.getLogPersonalitiesReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandPersonalitiesReloadFailure() + e.getMessage());
            plugin.getLogger().severe(languageManager.getLogAiRequestError() + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 plugins 命令（第三方插件调用）
     *
     * <p>支持两种模式：</p>
     * <ul>
     *   <li>请求模式：/kilacraft plugins <人格> <内容> <玩家 UUID> - 生成AI回复</li>
     *   <li>获取模式：/kilacraft plugins get <人格> <玩家 UUID> - 获取最新AI回复</li>
     * </ul>
     * <p>此命令只能由控制台执行，玩家禁止使用</p>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handlePluginsCommand(CommandSender sender, String[] args) {
        // 检查是否为控制台执行
        if (sender instanceof Player player) {
            player.sendMessage(languageManager.getPluginCommandPlayerBlocked());
            plugin.getLogger().warning(languageManager.replacePlaceholders(languageManager.getLogPlayerCommandAttempt(), "player", player.getName()));
            return true;
        }

        // 检查是否有子命令
        if (args.length < 2) {
            sender.sendMessage(languageManager.getPluginCommandInsufficientArgs());
            sender.sendMessage(languageManager.getPluginCommandUsageExample());
            sender.sendMessage(languageManager.getPluginCommandUsageRequest());
            sender.sendMessage(languageManager.getPluginCommandUsageGet());
            return true;
        }

        // 判断是否为 get 子命令
        if ("get".equalsIgnoreCase(args[1])) {
            return handlePluginsGetCommand(sender, args);
        }

        // 请求模式：检查参数数量（至少需要 3 个参数：人格、内容、UUID，可选第 4 个参数：回调命令）
        if (args.length < 4) {
            sender.sendMessage(languageManager.getPluginCommandInsufficientArgs());
            sender.sendMessage(languageManager.getPluginCommandUsageExample());
            sender.sendMessage(languageManager.getPluginCommandUsageRequest());
            return true;
        }

        String personality = args[1];
        String message = args[2];
        String uuidString = args[3];
        // 可选的回调命令（第 5 个参数，索引为 4）
        String callbackCommand = args.length >= 5 ? args[4] : null;

        // 解析玩家 UUID
        UUID targetPlayerId;
        try {
            targetPlayerId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(languageManager.getPluginCommandInvalidUuid() + uuidString);
            sender.sendMessage(languageManager.getPluginCommandUuidFormatHint());
            return true;
        }

        // 获取目标玩家名称（用于显示和系统提示词）
        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerId);
        String targetPlayerName;
        if (targetPlayer != null) {
            targetPlayerName = targetPlayer.getName();
        } else {
            // 玩家不在线，尝试从离线数据获取
            targetPlayerName = plugin.getServer().getOfflinePlayer(targetPlayerId).getName();
            if (targetPlayerName == null) {
                targetPlayerName = "Unknown";
            }
        }

        // 检查人格是否存在
        PersonalitiesConfigManager personalitiesConfig = plugin.getPersonalitiesConfigManager();
        if (!personalitiesConfig.hasPersonality(personality)) {
            sender.sendMessage(languageManager.getPluginCommandPersonalityNotFound() + personality);
            sender.sendMessage(languageManager.getPluginCommandPersonalityListHint() + String.join(", ", personalitiesConfig.getAllPersonalities()));
            return true;
        }

        // 如果目标玩家在线，检查世界限制
        if (targetPlayer != null) {
            if (!validator.canUseAIInWorld(targetPlayer)) {
                sender.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
                return true;
            }
        }

        // 检查插件命令专用冷却时间（基于 UUID，不管玩家是否在线）
        if (!validator.isPluginCommandCooldownReady(targetPlayerId)) {
            long remainingSeconds = validator.getPluginCommandRemainingCooldownSeconds(targetPlayerId);
            if (remainingSeconds > 0) {
                sender.sendMessage(languageManager.replacePlaceholders(languageManager.getPluginCommandCooldownWarning(), "player", targetPlayerName, "seconds", String.valueOf(remainingSeconds)));
            }
            return true;
        }

        // 生成隔离的历史记录 key（UUID_人格）
        String historyKey = validator.getPluginCommandHistoryKey(targetPlayerId, personality);

        // 获取或创建历史记录（使用隔离的 key）
        Deque<ConversationManager.Message> pluginHistory = getOrCreatePluginHistory(historyKey);

        // 立即更新冷却时间（基于 UUID）
        validator.startCooldown(targetPlayerId);

        // 创建插件命令响应处理器
        AIResponseHandler handler = new PluginCommandResponseHandler(sender, targetPlayerId, targetPlayerName);

        // 获取人格提示词并替换玩家名称占位符
        final String finalPersonality = personality;
        final String finalTargetPlayerName = targetPlayerName;
        String personalityPrompt = personalitiesConfig.getPersonalityPrompt(personality).replace("{player}", targetPlayerName);

        // 调试模式日志
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 插件命令请求 - 人格：" + personality + ", 玩家：" + targetPlayerName + ", UUID: " + targetPlayerId);
            plugin.getLogger().info("[DEBUG] 人格提示词：" + personalityPrompt);
            plugin.getLogger().info("[DEBUG] 历史记录数量：" + pluginHistory.size());
        }

        // 使用统一的 API 处理请求（传入人格提示词）
        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(message, targetPlayerName, pluginHistory, handler, personalityPrompt).thenAccept(fullResponse -> {
            // 保存对话到历史记录（隔离的），并保存到最新回复缓存
            validator.saveToHistory(pluginHistory, message, fullResponse, targetPlayerId, finalPersonality);

            // 优先执行回调命令（如果指定了）- 配置驱动型插件集成
            if (callbackCommand != null && !callbackCommand.isEmpty()) {
                executeCallback(callbackCommand, fullResponse);
                // 立即删除缓存
                plugin.getConversationManager().pollLatestAIResponse(targetPlayerId, finalPersonality);
            } else {
                // 没有回调命令，触发 Bukkit Event - 事件通知
                AIResponseReadyEvent event = new AIResponseReadyEvent(targetPlayerId, finalTargetPlayerName, finalPersonality, fullResponse);
                Bukkit.getPluginManager().callEvent(event);
                // 立即删除缓存
                plugin.getConversationManager().pollLatestAIResponse(targetPlayerId, finalPersonality);

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Bukkit Event 已触发");
                }
            }

            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 插件命令响应完成，响应长度：" + fullResponse.length());
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
            plugin.getLogger().severe(languageManager.getLogPluginCommandAiError() + throwable.getMessage());
            return null;
        });

        return true;
    }

    /**
     * 处理 plugins get 子命令（获取最新AI回复）
     *
     * <p>命令格式：/kilacraft plugins get <人格> <玩家 UUID></p>
     * <p>功能：获取指定人格的最新AI回复（获取后立即删除，一次性消费）</p>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handlePluginsGetCommand(CommandSender sender, String[] args) {
        // 检查参数数量（get 人格 UUID）
        if (args.length < 4) {
            sender.sendMessage(languageManager.getPluginCommandInsufficientArgs());
            sender.sendMessage(languageManager.getPluginCommandUsageExample());
            sender.sendMessage(languageManager.getPluginCommandUsageGet());
            return true;
        }

        String personality = args[2];
        String uuidString = args[3];

        // 解析玩家 UUID
        UUID targetPlayerId;
        try {
            targetPlayerId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(languageManager.getPluginCommandInvalidUuid() + uuidString);
            sender.sendMessage(languageManager.getPluginCommandUuidFormatHint());
            return true;
        }

        // 检查人格是否存在
        PersonalitiesConfigManager personalitiesConfig = plugin.getPersonalitiesConfigManager();
        if (!personalitiesConfig.hasPersonality(personality)) {
            sender.sendMessage(languageManager.getPluginCommandPersonalityNotFound() + personality);
            sender.sendMessage(languageManager.getPluginCommandPersonalityListHint() + String.join(", ", personalitiesConfig.getAllPersonalities()));
            return true;
        }

        // 获取并删除 AI 回复（一次性消费）
        String response = plugin.getConversationManager().pollLatestAIResponse(targetPlayerId, personality);

        // 调试模式日志
        if (plugin.getConfigManager().isDebugMode()) {
            if (response != null) {
                plugin.getLogger().info("[DEBUG] plugins get 获取到 AI 回复 - 玩家: " + targetPlayerId + ", 人格: " + personality + ", 回复长度: " + response.length());
            } else {
                plugin.getLogger().info("[DEBUG] plugins get 未找到 AI 回复（缓存为空或已被消费）- 玩家: " + targetPlayerId + ", 人格: " + personality);
            }
        }

        // 返回结果（适配轮询机制）
        // 如果没有数据，说明 AI 正在思考还未回复，返回等待标识
        sender.sendMessage(Objects.requireNonNullElse(response, "UNDEFINED"));

        return true;
    }

    /**
     * 获取或创建插件命令的历史记录（线程安全）
     *
     * @param historyKey 历史记录 key（格式：UUID_人格）
     * @return 历史记录队列
     */
    private Deque<ConversationManager.Message> getOrCreatePluginHistory(String historyKey) {
        return plugin.getConversationManager().getOrCreatePluginHistory(historyKey);
    }

    /**
     * 执行回调命令（配置驱动型插件集成）
     *
     * @param callbackCommand 回调命令模板（支持 {response} 占位符）
     * @param response        AI 回复内容
     */
    private void executeCallback(String callbackCommand, String response) {
        try {
            // 替换占位符
            String finalCommand = callbackCommand.replace("{response}", response.replace("\"", "\\\""));

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 执行回调命令: " + finalCommand);
            }

            // 以控制台身份执行回调命令
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 回调命令执行成功");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("执行回调命令失败: " + e.getMessage());
            plugin.getLogger().warning("原始命令: " + callbackCommand);
        }
    }

    /**
     * 处理普通消息命令
     */
    private boolean handleNormalMessageCommand(CommandSender sender, String[] args) {
        // 检查是否为玩家（非玩家跳过冷却和 API 调用）
        if (sender instanceof Player player) {
            return handlePlayerMessageCommand(player, args);
        } else {
            return handleConsoleMessageCommand(sender, args);
        }
    }

    /**
     * 处理玩家消息命令
     */
    private boolean handlePlayerMessageCommand(Player player, String[] args) {
        // 检查世界限制
        if (!validator.canUseAIInWorld(player)) {
            player.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
            return true;
        }

        // 检查冷却时间
        UUID playerId = player.getUniqueId();
        if (!validator.isCooldownReady(playerId)) {
            long remainingSeconds = validator.getRemainingCooldownSeconds(playerId);
            if (remainingSeconds > 0) {
                player.sendMessage(languageManager.replacePlaceholders(languageManager.getCooldownWarning(), "seconds", String.valueOf(remainingSeconds)));
            }
            return true;
        }

        // 获取或创建历史记录（同步块保证线程安全）
        Deque<ConversationManager.Message> playerHistory = getOrCreateHistory(playerId);

        // 调试模式：打印历史记录信息
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 构建消息
        String message = String.join(" ", args);

        // 发送"正在思考"消息
        MessageUtil.sendThinkingMessage(player);
        // 立即更新冷却时间
        validator.startCooldown(playerId);

        // 使用统一的 AI 请求处理器
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
        aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent);
        return true;
    }

    /**
     * 获取或创建历史记录（线程安全）
     */
    private Deque<ConversationManager.Message> getOrCreateHistory(UUID playerId) {
        ConversationManager convManager = plugin.getConversationManager();
        Deque<ConversationManager.Message> history = convManager.getHistory(playerId);

        if (history == null) {
            // 使用 computeIfAbsent 保证线程安全
            history = convManager.getHistory().computeIfAbsent(playerId, k -> new ArrayDeque<>());
        }

        return history;
    }

    /**
     * 处理控制台消息命令（支持与玩家相同的完整功能，无冷却和世界限制）
     */
    private boolean handleConsoleMessageCommand(CommandSender sender, String[] args) {
        // 构建消息
        String message = String.join(" ", args);

        // 发送"正在思考"消息
        MessageUtil.sendThinkingMessage(sender);

        // 使用统一的 AI 请求处理器（传入 null 表示控制台）
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
        aiRequestHandler.handleAIRequestForConsole(sender, message, enableAgent);
        return true;
    }

    /**
     * 获取发送者名称（安全处理）
     */
    private String getSenderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "Console";
    }

}
