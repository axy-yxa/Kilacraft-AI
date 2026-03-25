package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.listener.ChatListener;
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

    private static final String[] HELP_MESSAGES = {"§e使用方法：/kilacraft <消息>", "§e简写：/kila <消息> 或者 /ai <消息> 或者 /zm <消息>", "§e进入连续对话模式：/kilacraft chat", "§e清除历史：/kilacraft clear", "§e重载配置：/kilacraft reload", "§e重载知识库：/kilacraft knowledge reload"};

    private static final String PERMISSION_RELOAD = "kilacraft.reload";
    private static final String PERMISSION_CLEAR = "kilacraft.clear";
    private static final String PERMISSION_KNOWLEDGE = "kilacraft.knowledge";

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
            case "clear" -> handleClearCommand(sender);
            case "chat" -> handleChatCommand(sender, configManager);
            case "knowledge" -> handleKnowledgeCommand(sender, args);
            default ->
                // 普通消息发送命令
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
     */
    private boolean handleClearCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        if (!sender.hasPermission(PERMISSION_CLEAR)) {
            sender.sendMessage("§c你没有权限清除对话历史记录！");
            return true;
        }

        UUID playerId = player.getUniqueId();
        plugin.getChatListener().clearHistory(playerId);
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
        boolean inChatMode = plugin.getChatListener().isInChatMode(playerId);
        plugin.getChatListener().setChatMode(playerId, !inChatMode);

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
        Deque<ChatListener.Message> playerHistory = getOrCreateHistory(playerId);

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
    private Deque<ChatListener.Message> getOrCreateHistory(UUID playerId) {
        ChatListener chatListener = plugin.getChatListener();
        Deque<ChatListener.Message> history = chatListener.getHistory(playerId);

        if (history == null) {
            // 使用 computeIfAbsent 保证线程安全
            history = chatListener.getHistoryMap().computeIfAbsent(playerId, k -> new ArrayDeque<>());
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
