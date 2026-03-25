package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.listener.ChatListener;
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
    private final Map<UUID, Long> cooldowns;

    public KilacraftCommand(KilacraftAI plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e使用方法：/kilacraft <消息>");
            sender.sendMessage("§e简写：/kila <消息> 或者 /ai <消息> 或者 /zm <消息>");
            if (plugin.getConfigManager().isEnableChatCommand()) {
                sender.sendMessage("§e进入对话模式：/kilacraft chat");
            }
            sender.sendMessage("§e清除历史：/kilacraft clear");
            sender.sendMessage("§e重载配置：/kilacraft reload");
            return true;
        }
    
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("kilacraft.reload")) {
                sender.sendMessage("§c 你没有权限重载配置！");
                return true;
            }
            
            plugin.getConfigManager().loadConfig();
            sender.sendMessage("§a 配置已重载！");
            return true;
        }
                
        // 清除历史记录命令
        if (args[0].equalsIgnoreCase("clear")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c 只有玩家才能使用此命令！");
                return true;
            }
                    
            UUID playerId = player.getUniqueId();
            plugin.getChatListener().clearHistory(playerId);
            player.sendMessage("§a 已清除你的对话历史记录！");
            return true;
        }
    
        // 检查 chat 命令（进入对话模式）
        if (args[0].equalsIgnoreCase("chat")) {
            if (!plugin.getConfigManager().isEnableChatCommand()) {
                sender.sendMessage("§c聊天命令模式已被禁用！");
                return true;
            }
            
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c只有玩家才能使用对话模式！");
                return true;
            }
            
            // 切换对话模式状态
            UUID playerId = player.getUniqueId();
            boolean inChatMode = plugin.getChatListener().isInChatMode(playerId);
            plugin.getChatListener().setChatMode(playerId, !inChatMode);
            
            if (!inChatMode) {
                player.sendMessage("§a已进入对话模式！现在你说的每句话都会发送给 Kilacraft-AI。");
                player.sendMessage("§7输入 §e/kilacraft chat§7 退出对话模式");
            } else {
                player.sendMessage("§7已退出对话模式");
            }
            return true;
        }
    
        // 普通消息发送命令（不需要检查 enable_chat_command）

        // 检查是否为玩家（非玩家跳过冷却和 API 调用）
        if (sender instanceof Player player) {
            
            // 检查世界限制
            String worldName = player.getWorld().getName();
            List<String> allowedWorlds = plugin.getConfigManager().getAllowedWorlds();
            List<String> bannedWorlds = plugin.getConfigManager().getBannedWorlds();
            
            // 优先检查禁止列表
            if (!bannedWorlds.isEmpty() && bannedWorlds.contains(worldName)) {
                // 调试模式：打印玩家信息
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] [世界限制] 玩家 " + player.getName() + " 在禁止的世界 " + worldName + " 尝试使用 Kilacraft-AI（命令模式）");
                }
                player.sendMessage("§c 当前世界禁止使用 Kilacraft-AI！");
                return true;
            }
            
            // 再检查允许列表（如果为空表示所有世界都允许）
            if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
                // 调试模式：打印玩家信息
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] [世界限制] 玩家 " + player.getName() + " 在未授权的世界 " + worldName + " 尝试使用 Kilacraft-AI（命令模式）");
                }
                player.sendMessage("§c 当前世界禁止使用 Kilacraft-AI！");
                return true;
            }

            // 检查冷却时间
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            int cooldownSeconds = plugin.getConfigManager().getCooldownSeconds();

            if (cooldownSeconds > 0 && cooldowns.containsKey(playerId)) {
                long lastUsed = cooldowns.get(playerId);
                long timeLeft = (lastUsed + (cooldownSeconds * 1000L)) - currentTime;

                if (timeLeft > 0) {
                    player.sendMessage("§c请等待 " + (timeLeft / 1000) + " 秒后再试！");
                    return true;
                }
            }

            // 构建消息
            String message = String.join(" ", args);
            
            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 收到玩家命令请求：" + player.getName() + " - " + message);
            }
            
            // 获取历史记录（普通命令模式也支持历史）
            // 匿名变量
            var ref = new Object() {
                Deque<ChatListener.Message> playerHistory =
                        plugin.getChatListener().getHistory(playerId);
            };
            if (ref.playerHistory == null) {
                ref.playerHistory = new ArrayDeque<>();
                // 重要：将新创建的历史记录放入 Map 中，否则下次获取还是 null
                plugin.getChatListener().setHistory(playerId, ref.playerHistory);
            }
            
            // 调试模式：打印历史记录信息
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] [普通命令] 玩家 " + player.getName() + " 的历史记录数量：" + ref.playerHistory.size());
            }
            
            player.sendMessage("§7[Kilacraft-AI] §f正在思考中...");

            // 立即更新冷却时间（在发送请求时）
            cooldowns.put(playerId, currentTime);

            // 发送 API 请求（带历史记录）
            plugin.getDeepSeekAPI().sendMessage(message, player.getName(), ref.playerHistory).thenAccept(response -> {
                // 调试模式日志
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 收到 AI 响应，长度：" + response.length());
                }
                
                // 格式化响应，每 40 个字符换行
                String formattedResponse = formatResponse(response);
                player.sendMessage("§b[Kilacraft-AI] §f" + formattedResponse);
                
                // 保存对话到历史记录（使用之前获取的同一引用）
                int maxHistory = plugin.getConfigManager().getMaxHistory();
                if (maxHistory > 0) {
                    // 添加用户消息
                    ref.playerHistory.add(new com.zm.kilacraftAI.listener.ChatListener.Message("user", message));
                    // 添加 AI 回复
                    ref.playerHistory.add(new com.zm.kilacraftAI.listener.ChatListener.Message("assistant", response));
                    
                    // 保持历史记录不超过限制
                    while (ref.playerHistory.size() > maxHistory * 2) {
                        com.zm.kilacraftAI.listener.ChatListener.Message removed = ref.playerHistory.removeFirst();
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[DEBUG] [普通命令] 移除最早的历史记录：" + removed.getContent().substring(0, Math.min(20, removed.getContent().length())) + "...");
                        }
                    }
                    
                    // 调试模式：打印保存后的历史记录
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] [普通命令] 已保存新对话，当前历史记录数量：" + ref.playerHistory.size());
                    }
                }
            }).exceptionally(throwable -> {
                player.sendMessage("§c发生错误：" + throwable.getMessage());
                return null;
            });
        } else {
            // 后台控制台使用
            String message = String.join(" ", args);
            
            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 收到控制台命令请求 - " + message);
            }
            
            sender.sendMessage("§7[Kilacraft-AI] §f正在思考中...");

            plugin.getDeepSeekAPI().sendMessage(message, "Console").thenAccept(response -> {
                // 调试模式日志
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 收到 AI 响应（控制台），长度：" + response.length());
                }
                
                String formattedResponse = formatResponse(response);
                sender.sendMessage("§b[Kilacraft-AI] §f" + formattedResponse);
            }).exceptionally(throwable -> {
                sender.sendMessage("§c发生错误：" + throwable.getMessage());
                return null;
            });
        }

        return true;
    }

    private String formatResponse(String response) {
        StringBuilder formatted = new StringBuilder();
        String[] words = response.split(" ");
        int lineLength = 0;

        for (String word : words) {
            if (lineLength + word.length() > 40) {
                formatted.append("\n");
                lineLength = 0;
            }
            formatted.append(word).append(" ");
            lineLength += word.length() + 1;
        }

        return formatted.toString().trim();
    }
}