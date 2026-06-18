package com.zm.kilacraftAI.handler;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.AIRequestValidatorUtil;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.framework.*;
import com.zm.kilacraftAI.skills.framework.resume.PendingAction;
import com.zm.kilacraftAI.skills.framework.resume.PendingResume;
import com.zm.kilacraftAI.skills.framework.resume.PendingResumeManager;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final AIRequestValidatorUtil validator;
    private final LanguageManager languageManager;

    public AIRequestHandler(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidatorUtil(plugin);
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * 处理玩家 AI 请求（默认私信回复）
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent) {
        handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSourceEnum.CHAT);
    }

    /**
     * 处理玩家 AI 请求
     *
     * @param publicReply 是否将AI回复广播给所有在线玩家（公屏回复）
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent, boolean publicReply) {
        handleAIRequest(player, message, playerHistory, enableAgent, publicReply, ConversationSourceEnum.CHAT);
    }

    /**
     * 处理玩家 AI 请求（含 source 参数）
     *
     * @param publicReply 是否将AI回复广播给所有在线玩家（公屏回复）
     * @param source      来源标识
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent, boolean publicReply, ConversationSourceEnum source) {
        // 使用统一的响应管线
        AIResponsePipeline pipeline = plugin.getResponsePipeline();

        java.util.function.Consumer<String> sendResponse;
        java.util.function.Consumer<String> sendError;

        if (publicReply) {
            // 公屏模式：先发送给触发者（使用场景配置），再广播给所有人
            sendResponse = response -> {
                // 1. 发送给触发者（使用 NORMAL_CHAT 场景配置，如 ACTION_BAR/SIDEBAR/CHAT）
                pipeline.send(player, response, OutputScenarioEnum.NORMAL_CHAT);
                // 2. 公屏广播（强制 CHAT）
                // 如果NORMAL_CHAT的载体是CHAT，需要排除触发者避免重复；否则传null让所有人都收到
                boolean isChatChannel = (pipeline.getChannelForScenario(OutputScenarioEnum.NORMAL_CHAT) == OutputChannelEnum.CHAT);
                pipeline.broadcast(response, isChatChannel ? player : null);
            };
        } else {
            // 私信模式：只发给触发者
            sendResponse = response -> pipeline.send(player, response, OutputScenarioEnum.NORMAL_CHAT);
        }
        sendError = error -> pipeline.sendError(player, languageManager.getPluginCommandError() + error);

        RequestContext ctx = new RequestContext(player.getName(), player, playerHistory, sendResponse, sendError, OutputScenarioEnum.NORMAL_CHAT, publicReply, source);
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理控制台 AI 请求
     */
    public void handleAIRequestForConsole(CommandSender sender, String message, boolean enableAgent) {
        UUID consoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Deque<ConversationManager.Message> consoleHistory = plugin.getConversationManager().getOrCreateHistory(consoleUUID);

        RequestContext ctx = new RequestContext("Console", null, consoleHistory, response -> sender.sendMessage(MessageUtil.getAIPrefix() + MessageUtil.convertMarkdownToMinecraft(response)), error -> sender.sendMessage(languageManager.getPluginCommandError() + error), OutputScenarioEnum.NORMAL_CHAT, false, ConversationSourceEnum.CONSOLE);
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理 AI 请求（内部统一逻辑）
     */
    private void handleAIRequestInternal(String message, RequestContext ctx, boolean enableAgent) {
        // 校验 API Key 是否已配置
        if (!plugin.getConfigManager().isApiKeyConfigured()) {
            String hint = I18nService.tr("§c[AI请求] API Key 未配置！请编辑 plugins/Kilacraft-AI/llm.yml 中的 llm.api_key 后重启服务器或执行 /kilacraft reload");
            ctx.sendError.accept(hint);
            PluginLoggerUtil.warn("AI请求", "拒绝请求：API Key 未配置");
            return;
        }

        if (!enableAgent) {
            PluginLoggerUtil.debug("AI请求", "Agent 能力已禁用，进入普通 AI 处理");
            MetricsCollector.getInstance().recordRequestType("normal_chat");
            handleNormalAIRequest(message, ctx, null);
            return;
        }

        PluginLoggerUtil.debug("AI请求", "开始 LLM 意图识别，用户：{}, 消息：{}", ctx.name(), message);

        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer == null) {
            MetricsCollector.getInstance().recordRequestType("normal_chat");
            handleNormalAIRequest(message, ctx, null);
            return;
        }

        // 待确认续体恢复短路：玩家存在活跃槽位时，先分类本轮回复是否针对待确认操作
        if (ctx.player() != null) {
            PendingResume slot = PendingResumeManager.getInstance().get(ctx.player().getUniqueId());
            if (slot != null) {
                PluginLoggerUtil.debug("AI请求", "检测到待确认续体：{}.{}，进入恢复分类", slot.getSkillName(), slot.getAction());
                intentRecognizer.classifyPendingResponse(message, slot).orTimeout(60, TimeUnit.SECONDS).thenAccept(pa -> {
                    if (pa != null) {
                        // 恢复/补值/取消 → 计为一次 skill_execution（仅在此记一次，避免与 runNormalRecognition 双计）
                        MetricsCollector.getInstance().recordRequestType("skill_execution");
                        handlePendingAction(pa, message, ctx);
                    } else {
                        // 无关回复 → 正常两阶段识别（runNormalRecognition 内部记一次），槽位不动（靠 TTL 兜底）
                        runNormalRecognition(message, ctx);
                    }
                }).exceptionally(throwable -> {
                    PluginLoggerUtil.warn("AI请求", "待确认续体分类失败: {}", throwable.getMessage());
                    ctx.sendError.accept(I18nService.tr("待确认续体分类失败: {}", throwable.getMessage()));
                    return null;
                });
                return;
            }
        }

        runNormalRecognition(message, ctx);
    }

    /**
     * 正常两阶段意图识别 + 分发（TaskPlan / 单意图 / 回退普通 AI）。
     */
    private void runNormalRecognition(String message, RequestContext ctx) {
        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer == null) {
            MetricsCollector.getInstance().recordRequestType("normal_chat");
            handleNormalAIRequest(message, ctx, null);
            return;
        }
        intentRecognizer.recognizeIntent(message, ctx.history(), ctx.name(), ctx.player()).orTimeout(120, TimeUnit.SECONDS).thenAccept(result -> {
            if (result instanceof TaskPlan taskPlan && taskPlan.isMultiStep()) {
                MetricsCollector.getInstance().recordRequestType("skill_execution");
                handleTaskPlan(taskPlan, message, ctx);
            } else if (result instanceof SkillIntent intent && intent.isValid()) {
                MetricsCollector.getInstance().recordRequestType("skill_execution");
                handleSkillIntent(intent, message, ctx);
            } else {
                PluginLoggerUtil.debug("AI请求", "意图识别结束，回退到普通 AI 处理");
                MetricsCollector.getInstance().recordRequestType("normal_chat");
                handleNormalAIRequest(message, ctx, null);
            }
        }).exceptionally(throwable -> {
            PluginLoggerUtil.warn("AI请求", "意图识别失败: {}", throwable.getMessage());
            ctx.sendError.accept(I18nService.tr("意图识别失败: {}", throwable.getMessage()));
            return null;
        });
    }

    /**
     * 处理任务计划（多步骤）
     */
    private void handleTaskPlan(TaskPlan taskPlan, String message, RequestContext ctx) {
        PluginLoggerUtil.debug("AI请求", "识别到多步骤任务：{}", taskPlan.getGoal());

        TaskExecutor taskExecutor = new TaskExecutor(plugin.getSkillManager());
        SkillContext context = new SkillContext(ctx.player(), null, new HashMap<>());

        // TaskExecutor 返回 AnalysisSummary，通过中间层进行 LLM 二次分析
        taskExecutor.executeTask(taskPlan, context, ctx.history(), message).thenAccept(summary -> {
            PluginLoggerUtil.debug("AI请求", "任务计划执行完成，开始 LLM 二次分析...");

            // 通过中间层输出（验证通过后已显示占位符，这里不需要再显示）
            plugin.getLlmOutputCoordinator().outputAnalysisResult(ctx.player(), summary, context, ctx.history(), OutputScenarioEnum.TASK_RESULT, false).thenAccept(result -> {
                // 保存历史记录
                validator.saveToHistory(ctx.history(), message, result.getMessage(), ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source());

                // 公屏广播（outputAnalysisResult只输出给触发者，公屏需要额外广播）
                // 如果场景载体是CHAT，需要排除触发者避免重复；否则传null让所有人都收到
                if (ctx.isBroadcast()) {
                    boolean isChatChannel = (plugin.getResponsePipeline().getChannelForScenario(OutputScenarioEnum.TASK_RESULT) == OutputChannelEnum.CHAT);
                    plugin.getResponsePipeline().broadcast(result.getMessage(), isChatChannel ? ctx.player() : null);
                }
            });
        });
    }

    /**
     * 处理技能意图（单意图）。
     */
    private void handleSkillIntent(SkillIntent intent, String message, RequestContext ctx) {
        PluginLoggerUtil.debug("AI请求", "识别到单意图：{}", intent.getAction());
        // 单意图路径不解析 {step_} 占位符（仅多步骤 TaskExecutor 解析）；残留说明 LLM 产出了残缺计划，
        // 参数无法执行，回退普通 AI 引导玩家给出具体值，避免占位符穿透到 skill 报"格式不正确"
        if (containsStepPlaceholder(intent.getEntities())) {
            PluginLoggerUtil.warn("AI请求", "单意图 {} 含未解析占位符 {}，回退普通 AI", intent.getAction(), intent.getEntities());
            SkillResult hint = SkillResult.failure(I18nService.tr("部分参数未能自动解析，请直接说明具体数值"));
            String enriched = message + "\n[系统提示：" + SkillResultFormatter.toLlmText(hint) + "]";
            handleNormalAIRequest(enriched, ctx, message);
            return;
        }
        SkillContext context = new SkillContext(ctx.player(), intent.getAction(), intent.getEntities());
        executeAndReport(context, intent, message, ctx, false);
    }

    /**
     * 单意图 entities 是否含未解析的 {step_} 占位符（多步骤构造，单意图无法解析）。
     */
    static boolean containsStepPlaceholder(Map<String, String> entities) {
        if (entities == null) return false;
        for (String v : entities.values()) {
            if (v != null && v.contains("{step_")) return true;
        }
        return false;
    }

    /**
     * 处理待确认续体的恢复动作。CONFIRM/RESPOND 用槽位快照（RESPOND 合并本轮新值）复用执行，
     * CANCEL 清槽位。终局清理由 {@link #executeAndReport} 处理。
     */
    private void handlePendingAction(PendingAction pa, String message, RequestContext ctx) {
        UUID pid = ctx.player().getUniqueId();

        // 原子认领（防并发重复消费）；已被认领/过期/取消则提示重试
        PendingResume slot = PendingResumeManager.getInstance().claim(pid);
        if (slot == null) {
            PluginLoggerUtil.debug("续体", "玩家 {} 的待确认续体已被并发认领/过期/取消", ctx.name());
            ctx.sendResponse.accept(I18nService.tr("该操作正在处理或已失效，请重新描述你的需求。"));
            return;
        }

        if (pa.getType() == PendingAction.Type.CANCEL) {
            // claim 已移除槽位，清理暂存 carry
            PendingResumeManager.getInstance().clear(pid);
            PluginLoggerUtil.debug("续体", "玩家 {} 取消待确认操作", ctx.name());
            ctx.sendResponse.accept(I18nService.tr("已取消该操作。"));
            return;
        }

        Map<String, String> entities = new HashMap<>(slot.getEntities());
        boolean confirmed = (pa.getType() == PendingAction.Type.CONFIRM);
        if (pa.getType() == PendingAction.Type.RESPOND && pa.getEntities() != null) {
            entities.putAll(pa.getEntities());
        }

        PluginLoggerUtil.debug("续体", "恢复执行：{}.{}（confirmed={}）", slot.getSkillName(), slot.getAction(), confirmed);
        SkillIntent intent = new SkillIntent(slot.getSkillName(), slot.getAction(), entities, 1.0, message);
        SkillContext context = new SkillContext(ctx.player(), slot.getAction(), entities).withConfirmed(confirmed).withAudit(message, "pending_resume");
        executeAndReport(context, intent, message, ctx, true);
    }

    /**
     * 执行单意图并输出结果（单意图与续体恢复共用）。
     *
     * @param resume 是否续体恢复：true 时 SUCCESS/FAILURE 终局清槽位；NEED_INFO 不清（由 executeSkillByIntent 自动刷新）
     */
    private void executeAndReport(SkillContext context, SkillIntent intent, String message, RequestContext ctx, boolean resume) {
        plugin.getSkillManager().executeSkillByIntent(intent, context).thenCompose(execResult -> {
            if (execResult.isSuccess()) {
                PluginLoggerUtil.debug("技能执行", "技能执行成功");
                return outputSingleSkillResult(execResult, context, message, ctx);
            }
            return CompletableFuture.completedFuture(execResult);
        }).thenAccept(finalResult -> {
            // 终局（SUCCESS/FAILURE）清槽位；NEED_INFO 不清（由 executeSkillByIntent 自动刷新）
            if (resume && ctx.player() != null && finalResult.getStatus() != SkillStatus.NEED_INFO) {
                PendingResumeManager.getInstance().clear(ctx.player().getUniqueId());
            }
            if (finalResult.isSuccess()) {
                // 保存历史记录
                validator.saveToHistory(ctx.history(), message, finalResult.getMessage(), ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source());
            } else {
                PluginLoggerUtil.debug("技能执行", "技能执行失败：{}", finalResult.getMessage());
                PluginLoggerUtil.debug("技能执行", "已回退到普通 AI 处理");
                // 将技能结果注入消息上下文，回退到普通 AI 兜底（LLM 据消息引导玩家）
                String enrichedMessage = message + "\n[系统提示：" + SkillResultFormatter.toLlmText(finalResult) + "]";
                handleNormalAIRequest(enrichedMessage, ctx, message);
            }
        }).exceptionally(throwable -> {
            if (resume && ctx.player() != null) {
                PendingResumeManager.getInstance().clear(ctx.player().getUniqueId());
            }
            ctx.sendError.accept(throwable.getMessage());
            PluginLoggerUtil.error("技能执行", I18nService.tr("技能执行异常: {}", throwable.getMessage()), throwable);
            return null;
        });
    }

    /**
     * 输出单技能执行结果（成功路径：构建摘要 → LLM 二次分析输出 → 公屏广播）。
     * 供 {@link #executeAndReport} 的单意图与恢复路径复用。
     */
    private CompletableFuture<SkillResult> outputSingleSkillResult(SkillResult execResult, SkillContext context, String message, RequestContext ctx) {
        AnalysisSummary summary = new AnalysisSummary().userMessage(message).addResult(execResult.getStatus().name(), execResult.getMessage()).statistics(1, 0, 0, 0);
        return plugin.getLlmOutputCoordinator().outputAnalysisResult(ctx.player(), summary, context, ctx.history(), OutputScenarioEnum.SKILL_RESULT, false).thenApply(result -> {
            if (ctx.isBroadcast()) {
                boolean isChatChannel = (plugin.getResponsePipeline().getChannelForScenario(OutputScenarioEnum.SKILL_RESULT) == OutputChannelEnum.CHAT);
                plugin.getResponsePipeline().broadcast(result.getMessage(), isChatChannel ? ctx.player() : null);
            }
            return result;
        });
    }

    /**
     * 处理普通 AI 请求（无技能调用）。
     *
     * @param message         发给 LLM 的消息（失败/需确认回退时会注入 [STATUS] 系统提示）
     * @param ctx             请求上下文
     * @param originalMessage 存入对话历史的用户原话；为 null 时存 message。
     *                        技能失败/需确认回退路径传入"未污染的原始消息"，避免把 [STATUS] 系统提示写进历史
     */
    private void handleNormalAIRequest(String message, RequestContext ctx, String originalMessage) {
        PluginLoggerUtil.debug("AI请求", "{} 的历史记录数量：{}", ctx.name(), ctx.history().size());

        String historyMessage = (originalMessage != null) ? originalMessage : message;

        AIResponseHandler handler;
        if (ctx.player() != null) {
            handler = new PlayerResponseHandler(plugin, ctx.player(), ctx.scenario(), ctx.sendResponse);
        } else {
            handler = new ConsoleResponseHandler(getSenderFromContext(ctx));
        }

        // 构建系统提示词（玩家时注入画像摘要，控制台不注入）
        String systemPrompt = plugin.getConfigManager().getSystemPrompt();
        if (ctx.player() != null) {
            var profileManager = plugin.getProfileManager();
            if (profileManager != null) {
                systemPrompt = profileManager.injectProfileSummary(systemPrompt, ctx.player().getUniqueId());
            }
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(message, ctx.name(), ctx.history(), handler, systemPrompt, true, true, false).orTimeout(120, TimeUnit.SECONDS).thenAccept(fullResponse -> validator.saveToHistory(ctx.history(), historyMessage, fullResponse, ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source())).exceptionally(throwable -> {
            PluginLoggerUtil.warn("AI请求", "LLM 请求失败: {}", throwable.getMessage());
            ctx.sendError.accept(I18nService.tr("LLM 请求失败: {}", throwable.getMessage()));
            return null;
        });
    }

    /**
     * 从上下文获取 CommandSender（用于控制台）
     */
    private CommandSender getSenderFromContext(RequestContext ctx) {
        return plugin.getServer().getConsoleSender();
    }

    /**
     * 请求上下文 - 统一玩家和控制台的差异
     */
    private record RequestContext(String name, Player player, Deque<ConversationManager.Message> history,
                                  java.util.function.Consumer<String> sendResponse,
                                  java.util.function.Consumer<String> sendError, OutputScenarioEnum scenario,
                                  boolean isBroadcast, ConversationSourceEnum source) {
    }
}
