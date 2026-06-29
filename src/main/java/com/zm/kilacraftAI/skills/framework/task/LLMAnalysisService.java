package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 分析服务 - 统一 Skill 执行结果的 LLM 二次分析逻辑
 *
 * <p>提供统一的 LLM 二次分析入口，支持历史对话上下文</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class LLMAnalysisService {

    private final KilacraftAI plugin;
    private final ConfigManager configManager;

    /**
     * 知识库检索禁用阈值：当分析提示词超过此长度时，说明技能执行结果已包含完整信息（如诊断报告全文），
     * 知识库检索的边际价值极低且增加噪音，应禁用。
     */
    private static final int KNOWLEDGE_DISABLE_THRESHOLD = 2000;

    public LLMAnalysisService() {
        this.plugin = KilacraftAI.getInstance();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * 分析执行结果并使用自定义 Handler 输出
     *
     * <p>由 LLMOutputCoordinator 调用，传入外部创建的 Handler</p>
     *
     * @param summary 统一的分析摘要
     * @param context 执行上下文
     * @param history 对话历史（用于上下文关联，可为 null）
     * @param handler 自定义响应处理器（由调用方创建）
     * @return 分析后的最终回复
     */
    public CompletableFuture<SkillResult> analyzeResultWithHandler(AnalysisSummary summary, SkillContext context, Deque<ConversationManager.Message> history, AIResponseHandler handler) {
        // responseFuture 提前创建：前置阶段（提示词构建/画像注入/Provider 获取）抛异常时也保证 Future 被 complete，避免调用链挂起
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        try {
        String promptContent = summary.buildPrompt();

        PluginLoggerUtil.debug("LLM分析", "LLM 二次分析 - 结果摘要:\n{}", promptContent);

        String playerName = context.getPlayer() != null ? context.getPlayer().getName() : "Console";

        // 构建分析提示词：执行结果 + 后缀
        String suffix = configManager.getAgentAnalysisPromptSuffix();
        String systemPrompt = configManager.getAgentSystemPrompt();

        // 画像注入
        var profileManager = plugin.getProfileManager();
        if (context.getPlayer() != null && profileManager != null) {
            systemPrompt = profileManager.injectProfileSummary(systemPrompt, context.getPlayer().getUniqueId());
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(promptContent);
        if (suffix != null && !suffix.isEmpty()) {
            promptBuilder.append(suffix);
        }

        String analysisPrompt = promptBuilder.toString();

        // 每次都获取最新的实例
        LLMProvider llmProvider = plugin.getLlmManager().getCurrentProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("LLM Provider 未初始化"));
        }

        // 包装 Handler，在完成 response 时同时完成 responseFuture
        AIResponseHandler wrapperHandler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return handler.getPlayerId();
            }

            @Override
            public String getPlayerName() {
                return handler.getPlayerName();
            }

            @Override
            public void showResponse(String response) {
                // 调用原始 Handler 的输出逻辑
                handler.showResponse(response);
                // 完成 Future（让调用链继续）
                responseFuture.complete(response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
                handler.showStreamChunk(chunk, currentMessage);
            }

            @Override
            public void handleError(String errorMessage) {
                try {
                    handler.handleError(errorMessage);
                } catch (Exception e) {
                    // 记录 Handler 错误处理异常，但不影响 Future 完成
                    PluginLoggerUtil.warn("LLM分析", I18nService.tr("Handler 错误处理异常: {}", e.getMessage()), e);
                } finally {
                    // 确保 Future 一定被完成，防止调用链挂起
                    responseFuture.completeExceptionally(new RuntimeException(errorMessage));
                }
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return handler.isStreamOutputEnabled();
            }
        };

        // 调用 LLM，传入包装后的 Handler
        // 当分析提示词已包含完整技能执行结果时（如诊断报告全文），知识库检索是纯噪音
        boolean enableKnowledge = analysisPrompt.length() < KNOWLEDGE_DISABLE_THRESHOLD;
        if (!enableKnowledge) {
            PluginLoggerUtil.debug("LLM分析", I18nService.tr("分析提示词较长（{}字符），跳过知识库检索以减少噪音", analysisPrompt.length()));
        }
        llmProvider.processRequestWithCustomSystemPrompt(analysisPrompt, playerName, history, wrapperHandler, systemPrompt, enableKnowledge, false, false);
        } catch (RuntimeException e) {
            // 前置阶段异常（非 LLM 调用本身）：记完整堆栈 + 通知 handler + 完成 Future，确保调用链不挂起
            PluginLoggerUtil.error("LLM分析", I18nService.tr("二次分析前置阶段异常: {}", e.getMessage()), e);
            try {
                handler.handleError(I18nService.tr("AI 分析时发生错误，请稍后重试"));
            } catch (Exception ignored) {
                // handler 自身错误处理失败不影响 Future 完成
            }
            responseFuture.completeExceptionally(e);
        }

        return responseFuture.thenApply(SkillResult::success);
    }

}
