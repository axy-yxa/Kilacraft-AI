package com.zm.kilacraftAI.handler;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillIntent;
import com.zm.kilacraftAI.skills.framework.task.LLMAnalysisService;
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
import java.util.concurrent.CompletableFuture;

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
    private final LLMAnalysisService analysisService;

    public AIRequestHandler(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
        this.analysisService = new LLMAnalysisService();
    }

    /**
     * 处理玩家 AI 请求
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent) {
        RequestContext ctx = new RequestContext(player.getName(), player, playerHistory, response -> player.sendMessage(MessageUtil.getAIPrefix() + response), error -> player.sendMessage(languageManager.getPluginCommandError() + error));
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理控制台 AI 请求
     */
    public void handleAIRequestForConsole(CommandSender sender, String message, boolean enableAgent) {
        UUID consoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Deque<ConversationManager.Message> consoleHistory = getOrCreateHistory(consoleUUID);

        RequestContext ctx = new RequestContext("Console", null, consoleHistory, response -> sender.sendMessage(MessageUtil.getAIPrefix() + response), error -> sender.sendMessage(languageManager.getPluginCommandError() + error));
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理 AI 请求（内部统一逻辑）
     */
    private void handleAIRequestInternal(String message, RequestContext ctx, boolean enableAgent) {
        if (!enableAgent) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Agent 能力已禁用，进入普通 AI 处理");
            }
            handleNormalAIRequest(message, ctx);
            return;
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 开始 LLM 意图识别，用户：" + ctx.name() + ", 消息：" + message);
        }

        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer == null) {
            handleNormalAIRequest(message, ctx);
            return;
        }

        intentRecognizer.recognizeIntent(message, ctx.history()).thenAccept(result -> {
            if (result instanceof TaskPlan taskPlan && taskPlan.isMultiStep()) {
                handleTaskPlan(taskPlan, message, ctx);
            } else if (result instanceof SkillIntent intent && intent.isValid()) {
                handleSkillIntent(intent, message, ctx);
            } else {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 意图识别结束，回退到普通 AI 处理");
                }
                handleNormalAIRequest(message, ctx);
            }
        }).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
            return null;
        });
    }

    /**
     * 处理任务计划（多步骤）
     */
    private void handleTaskPlan(TaskPlan taskPlan, String message, RequestContext ctx) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 识别到多步骤任务：" + taskPlan.getGoal());
        }

        TaskExecutor taskExecutor = new TaskExecutor(plugin.getSkillManager(), analysisService);
        SkillContext context = new SkillContext(ctx.player(), null, new HashMap<>());

        taskExecutor.executeTask(taskPlan, context, ctx.history()).thenAccept(execResult -> {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 任务计划执行完成：" + execResult.getMessage());
            }
            ctx.sendResponse.accept(execResult.getMessage());
            validator.saveToHistory(ctx.history(), message, execResult.getMessage());
        });
    }

    /**
     * 处理技能意图（单意图）
     */
    private void handleSkillIntent(SkillIntent intent, String message, RequestContext ctx) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 识别到单意图：" + intent.getAction());
        }

        SkillContext context = new SkillContext(ctx.player(), intent.getAction(), intent.getEntities());

        plugin.getSkillManager().executeSkillByIntent(intent, context).thenCompose(execResult -> {
            if (execResult.isSuccess()) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 技能执行成功");
                }
                String summary = "用户说：" + message + "\n\n[执行结果]\n- " + execResult.getMessage();
                return analysisService.analyzeResult(summary, context, ctx.history());
            } else {
                return CompletableFuture.completedFuture(execResult);
            }
        }).thenAccept(finalResult -> {
            if (finalResult.isSuccess()) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 技能执行完成：" + finalResult.getMessage());
                }
                ctx.sendResponse.accept(finalResult.getMessage());
                validator.saveToHistory(ctx.history(), message, finalResult.getMessage());
            } else {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] 技能执行失败：" + finalResult.getMessage());
                    plugin.getLogger().warning("[DEBUG] 已回退到普通 AI 处理");
                }
                handleNormalAIRequest(message, ctx);
            }
        }).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
            plugin.getLogger().severe("[技能执行异常] " + throwable.getMessage());
            return null;
        });
    }

    /**
     * 处理普通 AI 请求（无技能调用）
     */
    private void handleNormalAIRequest(String message, RequestContext ctx) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + ctx.name() + " 的历史记录数量：" + ctx.history().size());
        }

        AIResponseHandler handler;
        if (ctx.player() != null) {
            handler = new PlayerResponseHandler(ctx.player(), message, ctx.history());
        } else {
            handler = new ConsoleResponseHandler(getSenderFromContext(ctx));
        }

        plugin.getDeepSeekAPI().processRequest(message, ctx.name(), ctx.history(), handler).thenAccept(fullResponse -> {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] AI请求完成：" + fullResponse);
            }
            validator.saveToHistory(ctx.history(), message, fullResponse);
        }).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
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
            history = convManager.getHistory().computeIfAbsent(playerId, k -> new ArrayDeque<>());
        }
        return history;
    }

    /**
     * 从上下文获取 CommandSender（用于控制台）
     */
    private CommandSender getSenderFromContext(RequestContext ctx) {
        // 控制台场景下，通过回调推断 sender
        // 这里用一个简单的方式：控制台的player为null
        return plugin.getServer().getConsoleSender();
    }

    /**
     * 请求上下文 - 统一玩家和控制台的差异
     */
    private record RequestContext(String name, Player player, Deque<ConversationManager.Message> history,
                                  java.util.function.Consumer<String> sendResponse,
                                  java.util.function.Consumer<String> sendError) {
    }
}
