package com.zm.kilacraftAI.handler;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillIntent;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.UUID;

/**
 * AI 请求统一处理器
 *
 * <p>封装 LLM 意图识别和技能执行的通用逻辑，供 ChatListener 和 KilacraftCommand 复用</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-31
 */
public class AIRequestHandler {

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;
    private final LanguageManager languageManager;

    public AIRequestHandler(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * 处理 AI 请求（统一入口）
     *
     * @param player        玩家
     * @param message       消息内容
     * @param playerHistory 玩家历史记录
     * @param enableAgent   是否启用 Agent 能力
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent) {
        // 如果不启用 Agent 能力，直接进入普通 AI
        if (!enableAgent) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Agent 能力已禁用，进入普通 AI 处理");
            }
            handleNormalAIRequest(player, message, playerHistory);
            return;
        }

        // 调试模式：打印意图识别信息
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 开始 LLM 意图识别，玩家：" + player.getName() + ", 消息：" + message);
        }

        // 使用 LLM 识别意图（统一入口，支持单意图和多步骤任务）
        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer != null) {
            intentRecognizer.recognizeIntent(message, playerHistory).thenAccept(result -> {
                if (result instanceof TaskPlan taskPlan && taskPlan.isMultiStep()) {
                    // 是多步骤任务，使用任务执行器
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 识别到多步骤任务：" + taskPlan.getGoal());
                    }

                    var skillManager = plugin.getSkillManager();
                    TaskExecutor taskExecutor = new TaskExecutor(skillManager);
                    SkillContext context = new SkillContext(player, null, new HashMap<>());

                    taskExecutor.executeTask(taskPlan, context).thenAccept(execResult -> {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[DEBUG] 任务计划执行完成");
                            plugin.getLogger().info("[DEBUG] 执行结果：" + execResult.getMessage());
                        }
                        player.sendMessage(MessageUtil.getAIPrefix() + execResult.getMessage());
                        validator.saveToHistory(playerHistory, message, execResult.getMessage());
                    });
                } else if (result instanceof SkillIntent intent && intent.isValid()) {
                    // 是单意图，直接执行技能
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 识别到单意图：" + intent.getAction());
                    }
                    var skillManager = plugin.getSkillManager();
                    SkillContext context = new SkillContext(player, intent.getAction(), intent.getEntities());

                    skillManager.executeSkillByIntent(intent, context).thenAccept(execResult -> {
                        if (execResult.isSuccess()) {
                            // 技能执行成功
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().info("[DEBUG] 技能执行成功：" + intent.getAction());
                                plugin.getLogger().info("[DEBUG] 返回消息：" + execResult.getMessage());
                            }
                            player.sendMessage(MessageUtil.getAIPrefix() + execResult.getMessage());
                            validator.saveToHistory(playerHistory, message, execResult.getMessage());
                        } else {
                            // 技能执行失败，记录日志并回退到普通 AI 处理
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().warning("[DEBUG] 技能执行失败：" + intent.getAction());
                                plugin.getLogger().warning("[DEBUG] 失败原因：" + execResult.getMessage());
                                plugin.getLogger().warning("[DEBUG] 已回退到普通 AI 处理");
                            }
                            handleNormalAIRequest(player, message, playerHistory);
                        }
                    }).exceptionally(throwable -> {
                        player.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
                        plugin.getLogger().severe("[技能执行异常] " + throwable.getMessage());
                        return null;
                    });
                } else {
                    // 意图无效，回退到普通 AI 处理
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 意图识别结束，回退到普通 AI 处理");
                    }
                    handleNormalAIRequest(player, message, playerHistory);
                }
            }).exceptionally(throwable -> {
                player.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
                return null;
            });
        } else {
            // 意图识别器不可用，回退到普通 AI 处理
            handleNormalAIRequest(player, message, playerHistory);
        }
    }

    /**
     * 处理普通 AI 请求（无技能调用）
     *
     * @param player        玩家
     * @param message       消息内容
     * @param playerHistory 玩家历史记录
     */
    public void handleNormalAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory) {
        // 调试模式：打印当前历史记录
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 创建玩家响应处理器
        AIResponseHandler handler = new PlayerResponseHandler(player, message, playerHistory);

        // 使用统一的 API 处理请求
        plugin.getDeepSeekAPI().processRequest(message, player.getName(), playerHistory, handler).thenAccept(fullResponse -> {
            // 保存对话到历史记录
            validator.saveToHistory(playerHistory, message, fullResponse);
        }).exceptionally(throwable -> {
            player.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * 处理控制台 AI 请求（支持与玩家相同的完整功能，无冷却和世界限制）
     *
     * @param sender      命令发送者（控制台）
     * @param message     消息内容
     * @param enableAgent 是否启用 Agent 能力
     */
    public void handleAIRequestForConsole(CommandSender sender, String message, boolean enableAgent) {
        // 创建控制台专用的 UUID（固定值）
        var consoleUUID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        // 获取或创建控制台历史记录
        Deque<ConversationManager.Message> consoleHistory = getOrCreateHistory(consoleUUID);
        
        // 如果不启用 Agent 能力，直接进入普通 AI
        if (!enableAgent) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Agent 能力已禁用，进入普通 AI 处理");
            }
            handleNormalAIRequestForConsole(sender, message, consoleHistory);
            return;
        }
        
        // 调试模式：打印意图识别信息
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 开始 LLM 意图识别（控制台），消息：" + message);
        }
        
        // 使用 LLM 识别意图（统一入口，支持单意图和多步骤任务）
        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer != null) {
            intentRecognizer.recognizeIntent(message, consoleHistory).thenAccept(result -> {
                if (result instanceof TaskPlan taskPlan && taskPlan.isMultiStep()) {
                    // 是多步骤任务，使用任务执行器
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 识别到多步骤任务：" + taskPlan.getGoal());
                    }
                    
                    var skillManager = plugin.getSkillManager();
                    TaskExecutor taskExecutor = new TaskExecutor(skillManager);
                    // 传入 null 作为玩家对象（控制台没有玩家实体）
                    SkillContext context = new SkillContext(null, null, new HashMap<>());
                    
                    taskExecutor.executeTask(taskPlan, context).thenAccept(execResult -> {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[DEBUG] 任务计划执行完成");
                            plugin.getLogger().info("[DEBUG] 执行结果：" + execResult.getMessage());
                        }
                        sender.sendMessage(MessageUtil.getAIPrefix() + execResult.getMessage());
                        validator.saveToHistory(consoleHistory, message, execResult.getMessage());
                    });
                } else if (result instanceof SkillIntent intent && intent.isValid()) {
                    // 是单意图，直接执行技能
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 识别到单意图：" + intent.getAction());
                    }
                    var skillManager = plugin.getSkillManager();
                    // 传入 null 作为玩家对象（控制台没有玩家实体）
                    SkillContext context = new SkillContext(null, intent.getAction(), intent.getEntities());
                    
                    skillManager.executeSkillByIntent(intent, context).thenAccept(execResult -> {
                        if (execResult.isSuccess()) {
                            // 技能执行成功
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().info("[DEBUG] 技能执行成功：" + intent.getAction());
                                plugin.getLogger().info("[DEBUG] 返回消息：" + execResult.getMessage());
                            }
                            sender.sendMessage(MessageUtil.getAIPrefix() + execResult.getMessage());
                            validator.saveToHistory(consoleHistory, message, execResult.getMessage());
                        } else {
                            // 技能执行失败，记录日志并回退到普通 AI 处理
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().warning("[DEBUG] 技能执行失败：" + intent.getAction());
                                plugin.getLogger().warning("[DEBUG] 失败原因：" + execResult.getMessage());
                                plugin.getLogger().warning("[DEBUG] 已回退到普通 AI 处理");
                            }
                            handleNormalAIRequestForConsole(sender, message, consoleHistory);
                        }
                    }).exceptionally(throwable -> {
                        sender.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
                        plugin.getLogger().severe("[技能执行异常] " + throwable.getMessage());
                        return null;
                    });
                } else {
                    // 意图无效，回退到普通 AI 处理
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 意图识别结束，回退到普通 AI 处理");
                    }
                    handleNormalAIRequestForConsole(sender, message, consoleHistory);
                }
            }).exceptionally(throwable -> {
                sender.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
                return null;
            });
        } else {
            // 意图识别器不可用，回退到普通 AI 处理
            handleNormalAIRequestForConsole(sender, message, consoleHistory);
        }
    }
    
    /**
     * 处理控制台普通 AI 请求（无技能调用）
     *
     * @param sender        命令发送者（控制台）
     * @param message       消息内容
     * @param consoleHistory 控制台历史记录
     */
    private void handleNormalAIRequestForConsole(CommandSender sender, String message, Deque<ConversationManager.Message> consoleHistory) {
        // 调试模式：打印当前历史记录
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 控制台的历史记录数量：" + consoleHistory.size());
        }
        
        // 创建控制台响应处理器
        AIResponseHandler handler = new ConsoleResponseHandler(sender);
        
        // 使用统一的 API 处理请求
        plugin.getDeepSeekAPI().processRequest(message, "Console", consoleHistory, handler).thenAccept(fullResponse -> {
            // 保存对话到历史记录
            validator.saveToHistory(consoleHistory, message, fullResponse);
        }).exceptionally(throwable -> {
            sender.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
            return null;
        });
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
}
