package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.handler.impl.PluginCommandResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
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

    private static final String[] HELP_MESSAGES = {"§e 使用方法：/kilacraft <消息>", "§e 简写：/kila <消息> 或者 /ai <消息> 或者 /zm <消息>", "§e 进入连续对话模式：/kilacraft chat", "§e 重载配置：/kilacraft reload", "§e 重载知识库：/kilacraft knowledge reload", "§e 重载人格配置：/kilacraft personalities reload"};
    
    private static final String PERMISSION_RELOAD = "kilacraft.reload";
    private static final String PERMISSION_CLEAR_SELF = "kilacraft.clear.self";
    private static final String PERMISSION_CLEAR_OTHER = "kilacraft.clear.other";
    private static final String PERMISSION_KNOWLEDGE = "kilacraft.knowledge";
    private static final String PERMISSION_PERSONALITIES = "kilacraft.personalities";

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;

    public KilacraftCommand(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
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
        for (int i = 0; i < HELP_MESSAGES.length; i++) {
            // 跳过 chat 命令提示如果功能被禁用
            if (i == 2 && !chatCommandEnabled) {
                continue;
            }
            sender.sendMessage(HELP_MESSAGES[i]);
        }
        
        // 根据权限显示清除历史提示
        if (sender.hasPermission(PERMISSION_CLEAR_SELF)) {
            sender.sendMessage("§e 清除历史：/kilacraft clear");
        }
        if (sender.hasPermission(PERMISSION_CLEAR_OTHER)) {
            sender.sendMessage("§e 清除玩家历史：/kilacraft clear [玩家名称]");
        }
    }

    /**
     * 处理 reload 命令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage("§c你没有权限重载配置！");
            return true;
        }

        try {
            plugin.getConfigManager().loadConfig();
            sender.sendMessage("§a配置已重载！");
            plugin.getLogger().info("配置已由 " + getSenderName(sender) + " 重载");
        } catch (Exception e) {
            sender.sendMessage("§c配置重载失败：" + e.getMessage());
            plugin.getLogger().severe("配置重载失败：" + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 clear 命令
     * 
     * <p>支持清除自己或指定玩家的历史记录</p>
     * 
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 执行结果
     */
    private boolean handleClearCommand(CommandSender sender, String[] args) {
        // 如果提供了玩家名称参数，清除指定玩家的历史（需要 kilacraft.clear.other 权限）
        if (args.length >= 2) {
            if (!sender.hasPermission(PERMISSION_CLEAR_OTHER)) {
                sender.sendMessage("§c你没有权限清除其他玩家的历史记录！");
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
            sender.sendMessage("§a已清除玩家 " + targetPlayerName + " 的对话历史记录！");
            plugin.getLogger().info(getSenderName(sender) + " 已清除玩家 " + targetPlayerName + " 的对话历史记录");
            return true;
        }

        // 没有参数，清除自己的历史记录（需要 kilacraft.clear.self 权限）
        if (!sender.hasPermission(PERMISSION_CLEAR_SELF)) {
            sender.sendMessage("§c你没有权限清除自己的对话历史记录！");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c请在命令中添加玩家名称来清除其历史记录：/kilacraft clear <玩家名称>");
            return true;
        }

        UUID playerId = player.getUniqueId();
        plugin.getConversationManager().clearHistory(playerId);
        player.sendMessage("§a已清除你的对话历史记录！");
        plugin.getLogger().info("玩家 " + player.getName() + " 已清除对话历史记录");
        return true;
    }

    /**
     * 处理 chat 命令
     */
    private boolean handleChatCommand(CommandSender sender, ConfigManager configManager) {
        if (!configManager.isEnableChatCommand()) {
            sender.sendMessage("§c连续对话模式已被禁用！");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用连续对话模式！");
            return true;
        }

        // 切换对话模式状态
        UUID playerId = player.getUniqueId();
        ConversationManager convManager = plugin.getConversationManager();
        boolean inChatMode = convManager.isInChatMode(playerId);
        convManager.setChatMode(playerId, !inChatMode);

        if (!inChatMode) {
            player.sendMessage("§a已进入连续对话模式！现在你说的每句话都会发送给 Kilacraft-AI。");
            player.sendMessage("§7输入 §e/kilacraft chat§7 退出连续对话模式");
            plugin.getLogger().info("玩家 " + player.getName() + " 已进入连续对话模式");
        } else {
            player.sendMessage("§7已退出连续对话模式");
            plugin.getLogger().info("玩家 " + player.getName() + " 已退出连续对话模式");
        }
        return true;
    }

    /**
     * 处理 knowledge 命令
     */
    private boolean handleKnowledgeCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission(PERMISSION_KNOWLEDGE)) {
            sender.sendMessage("§c你没有权限管理知识库！");
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage("§e使用方法：/kilacraft knowledge reload");
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handleKnowledgeReloadCommand(sender);
        } else {
            sender.sendMessage("§c未知的子命令：" + subCommand);
            sender.sendMessage("§e可用子命令：reload");
            return true;
        }
    }

    /**
     * 处理 knowledge reload 命令
     */
    private boolean handleKnowledgeReloadCommand(CommandSender sender) {
        try {
            plugin.getKnowledgeBase().reload();
            sender.sendMessage("§a知识库已重载！");
            sender.sendMessage("§7" + plugin.getKnowledgeBase().getStatistics());
            plugin.getLogger().info("知识库已由 " + getSenderName(sender) + " 重载");
        } catch (Exception e) {
            sender.sendMessage("§c知识库重载失败：" + e.getMessage());
            plugin.getLogger().severe("知识库重载失败：" + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 personalities 命令（人格配置管理）
     * 
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 执行结果
     */
    private boolean handlePersonalitiesCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission(PERMISSION_PERSONALITIES)) {
            sender.sendMessage("§c你没有权限管理人设配置！");
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage("§e使用方法：/kilacraft personalities reload");
            sender.sendMessage("§e可用子命令：reload - 重新加载人格配置");
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handlePersonalitiesReloadCommand(sender);
        } else {
            sender.sendMessage("§c未知的子命令：" + subCommand);
            sender.sendMessage("§e可用子命令：reload");
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
            sender.sendMessage("§a人格配置已重载！");
            sender.sendMessage("§7当前共加载 " + plugin.getPersonalitiesConfigManager().getAllPersonalities().size() + " 个人格");
            plugin.getLogger().info("人格配置已由 " + getSenderName(sender) + " 重载");
        } catch (Exception e) {
            sender.sendMessage("§c人格配置重载失败：" + e.getMessage());
            plugin.getLogger().severe("人格配置重载失败：" + e.getMessage());
        }
        return true;
    }

    /**
     * 处理 plugins 命令（第三方插件调用）
     * 
     * <p>命令格式：/kilacraft plugins <人格> <内容> <玩家 UUID></p>
     * <p>此命令只能由控制台执行，玩家禁止使用</p>
     * 
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 执行结果
     */
    private boolean handlePluginsCommand(CommandSender sender, String[] args) {
        // 检查是否为控制台执行
        if (sender instanceof Player player) {
            player.sendMessage("§c请使用 /kilacraft <消息>");
            plugin.getLogger().warning("玩家 " + player.getName() + " 尝试执行控制台专用命令 /kilacraft plugins");
            return true;
        }

        // 检查参数数量（至少需要 3 个参数：人格、内容、UUID）
        if (args.length < 4) {
            sender.sendMessage("§c参数不足！使用方法：/kilacraft plugins <人格> <内容> <玩家 UUID>");
            sender.sendMessage("§e示例：/kilacraft plugins 严厉教师 你好 069a79f4-44e9-4726-a5be-fca90e38aaf5");
            return true;
        }

        String personality = args[1];
        String message = args[2];
        String uuidString = args[3];

        // 解析玩家 UUID
        UUID targetPlayerId;
        try {
            targetPlayerId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的玩家 UUID 格式：" + uuidString);
            sender.sendMessage("§e请确保 UUID 格式正确，例如：069a79f4-44e9-4726-a5be-fca90e38aaf5");
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
            sender.sendMessage("§c未找到人格配置：" + personality);
            sender.sendMessage("§e可用的人格列表：" + String.join(", ", personalitiesConfig.getAllPersonalities()));
            return true;
        }

        // 如果目标玩家在线，检查世界限制
        if (targetPlayer != null) {
            if (!validator.checkWorldLimitAndNotify(targetPlayer, "插件命令")) {
                sender.sendMessage("§c该玩家当前所在世界禁止使用 AI 功能！");
                return true;
            }
        }

        // 检查插件命令专用冷却时间（基于 UUID，不管玩家是否在线）
        if (!validator.checkPluginCommandCooldownAndNotify(sender, targetPlayerId, targetPlayerName)) {
            return true;
        }

        // 生成隔离的历史记录 key（UUID_人格）
        String historyKey = validator.getPluginCommandHistoryKey(targetPlayerId, personality);

        // 获取或创建历史记录（使用隔离的 key）
        Deque<ConversationManager.Message> pluginHistory = getOrCreatePluginHistory(historyKey);

        // 调试模式日志
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 插件命令请求 - 人格：" + personality + ", 玩家：" + targetPlayerName + ", UUID: " + targetPlayerId);
            plugin.getLogger().info("[DEBUG] 历史记录数量：" + pluginHistory.size());
        }

        // 发送"正在思考"消息到控制台
        MessageUtil.sendThinkingMessage(sender);

        // 立即更新冷却时间（基于 UUID）
        validator.startCooldown(targetPlayerId);

        // 创建插件命令响应处理器
        AIResponseHandler handler = new PluginCommandResponseHandler(sender, targetPlayerId, targetPlayerName);

        // 获取人格提示词并替换玩家名称占位符
        String personalityPrompt = personalitiesConfig.getPersonalityPrompt(personality).replace("{player}", targetPlayerName);
        
        // 使用统一的 API 处理请求（传入人格提示词）
        plugin.getDeepSeekAPI().processRequestWithCustomSystemPrompt(message, targetPlayerName, pluginHistory, handler, personalityPrompt)
            .thenAccept(fullResponse -> {
                // 保存对话到历史记录（隔离的），并保存到最新回复缓存
                validator.saveToHistory(pluginHistory, message, fullResponse, targetPlayerId, personality);
                
                // 调试模式日志
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 插件命令响应完成，响应长度：" + fullResponse.length());
                }
            }).exceptionally(throwable -> {
                sender.sendMessage("§c发生错误：" + throwable.getMessage());
                plugin.getLogger().severe("插件命令 AI 请求发生错误：" + throwable.getMessage());
                return null;
            });

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
        if (!validator.checkWorldLimitAndNotify(player, "命令模式")) {
            return true;
        }

        // 检查冷却时间
        UUID playerId = player.getUniqueId();
        if (!validator.checkCooldownAndNotify(player)) {
            return true;
        }

        // 获取或创建历史记录（同步块保证线程安全）
        Deque<ConversationManager.Message> playerHistory = getOrCreateHistory(playerId);

        // 调试模式：打印历史记录信息
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 发送"正在思考"消息（从配置文件读取）
        MessageUtil.sendThinkingMessage(player);

        // 立即更新冷却时间（在发送请求时）
        validator.startCooldown(playerId);

        // 构建消息
        String message = String.join(" ", args);

        // 创建玩家响应处理器
        AIResponseHandler handler = new PlayerResponseHandler(player, message, playerHistory);

        // 使用统一的 API 处理请求
        plugin.getDeepSeekAPI().processRequest(message, player.getName(), playerHistory, handler).thenAccept(fullResponse -> {
            // 保存对话到历史记录
            validator.saveToHistory(playerHistory, message, fullResponse);
        }).exceptionally(throwable -> {
            player.sendMessage("§c发生错误：" + throwable.getMessage());
            plugin.getLogger().severe("处理 AI 请求时发生错误：" + throwable.getMessage());
            return null;
        });

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
     * 处理控制台消息命令
     */
    private boolean handleConsoleMessageCommand(CommandSender sender, String[] args) {
        // 后台控制台使用
        String message = String.join(" ", args);

        // 发送"正在思考"消息（从配置文件读取）
        MessageUtil.sendThinkingMessage(sender);

        // 创建控制台响应处理器
        AIResponseHandler handler = new ConsoleResponseHandler(sender);

        // 使用统一的 API 处理请求
        plugin.getDeepSeekAPI().processRequest(message, "Console", null, handler).exceptionally(throwable -> {
            sender.sendMessage("§c发生错误：" + throwable.getMessage());
            plugin.getLogger().severe("控制台 AI 请求发生错误：" + throwable.getMessage());
            return null;
        });

        return true;
    }

    /**
     * 获取发送者名称（安全处理）
     */
    private String getSenderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "Console";
    }

}
