package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.util.HistoryUtil;

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

    public LLMAnalysisService() {
        this.plugin = KilacraftAI.getInstance();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * 分析执行结果并生成最终回复
     *
     * <p>调用方负责构建结果摘要，服务只负责调用 LLM 进行分析</p>
     *
     * @param resultsSummary 结果摘要（由调用方构建）
     * @param context        执行上下文
     * @param history        对话历史（用于上下文关联，可为 null）
     * @return 分析后的最终回复
     */
    public CompletableFuture<SkillResult> analyzeResult(String resultsSummary, SkillContext context, Deque<ConversationManager.Message> history) {
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] LLM 二次分析 - 结果摘要:\n" + resultsSummary);
        }

        String playerName = context.getPlayer() != null ? context.getPlayer().getName() : "Console";
        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        // 构建分析提示词：执行结果 + 后缀
        String suffix = configManager.getAgentAnalysisPromptSuffix();
        String systemPrompt = configManager.getAgentSystemPrompt();
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(resultsSummary);
        if (suffix != null && !suffix.isEmpty()) {
            promptBuilder.append(suffix);
        }
        
        String analysisPrompt = promptBuilder.toString();

        // 每次都获取最新的实例
        LLMProvider llmProvider = plugin.getLlmManager().getCurrentProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("LLM Provider 未初始化"));
        }

        // 调用 LLM，传入历史对话，由 GenericLLMProvider 内部处理知识库检索和历史注入
        llmProvider.processRequestWithCustomSystemPrompt(analysisPrompt, playerName, history, createAnalysisHandler(playerName, responseFuture), systemPrompt, true, false);
        return responseFuture.thenApply(SkillResult::success);
    }

    /**
     * 创建分析用的 Handler
     */
    private AIResponseHandler createAnalysisHandler(String playerName, CompletableFuture<String> responseFuture) {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return null;
            }

            @Override
            public String getPlayerName() {
                return playerName;
            }

            @Override
            public void showResponse(String response) {
                responseFuture.complete(response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
                // 分析阶段不需要流式输出
            }

            @Override
            public void handleError(String errorMessage) {
                responseFuture.completeExceptionally(new RuntimeException(errorMessage));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };
    }
}
