package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 输出协调器（中间层）
 *
 * <p>职责：统一调度 LLM 二次分析结果的输出</p>
 * <ul>
 *   <li>接收 LLMAnalysisService 的分析结果</li>
 *   <li>根据配置决定使用流式还是非流式输出</li>
 *   <li>统一调用 AIResponsePipeline 输出</li>
 *   <li>避免调用方重复输出</li>
 *   <li>处理占位符显示逻辑（主动请求显示，挂机回调不显示）</li>
 * </ul>
 *
 * <h3>架构定位：</h3>
 * <pre>
 * 调用方 → LLMOutputCoordinator → LLMAnalysisService → GenericLLMProvider
 *                                    ↓
 *                              Handler 回调
 *                                    ↓
 *                            AIResponsePipeline（输出）
 * </pre>
 *
 * @author Zm_Mmm
 * @since 2026-04-16
 */
public class LLMOutputCoordinator {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;
    private final AIResponsePipeline pipeline;
    private final LLMAnalysisService analysisService;

    public LLMOutputCoordinator(KilacraftAI plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.pipeline = plugin.getResponsePipeline();
        this.analysisService = new LLMAnalysisService();
    }

    /**
     * 输出 LLM 二次分析结果（统一入口）
     *
     * @param player          目标玩家
     * @param summary         分析摘要（结构化数据，面向 LLM）
     * @param context         执行上下文
     * @param history         对话历史（挂机任务传空）
     * @param scenario        输出场景
     * @param showPlaceholder 是否显示占位符（主动请求=true，挂机回调=false）
     * @return CompletableFuture<SkillResult> 分析结果
     */
    public CompletableFuture<SkillResult> outputAnalysisResult(Player player, AnalysisSummary summary, SkillContext context, Deque<ConversationManager.Message> history, OutputScenario scenario, boolean showPlaceholder) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(SkillResult.failure("玩家不在线"));
        }

        // 如果启用流式输出，启动流式状态机
        if (config.isStreamEnabled()) {
            OutputChannel channel = pipeline.getChannelForScenario(scenario);
            // 根据 showPlaceholder 决定是否显示占位符
            boolean silent = !showPlaceholder;  // showPlaceholder=false 时静默启动
            pipeline.startStream(player, channel, silent);
        } else if (showPlaceholder) {
            // 非流式模式：显示“正在思考”消息
            MessageUtil.sendThinkingMessage(player);
        }

        // 创建支持流式的 Handler
        AIResponseHandler handler = createStreamHandler(player, scenario);

        // 调用 LLM 分析（使用自定义 Handler）
        return analysisService.analyzeResultWithHandler(summary, context, history, handler);
    }

    /**
     * 输出错误消息
     *
     * @param player       目标玩家
     * @param errorMessage 错误消息
     */
    public void outputError(Player player, String errorMessage) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // 错误消息通过管线输出（支持配置化载体）
        pipeline.sendError(player, errorMessage);
    }

    /**
     * 创建支持流式输出的 Handler
     */
    private AIResponseHandler createStreamHandler(Player player, OutputScenario scenario) {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return player.getUniqueId();
            }

            @Override
            public String getPlayerName() {
                return player.getName();
            }

            @Override
            public void showResponse(String response) {
                // 流式模式：通过管线完成流式输出
                if (config.isStreamEnabled()) {
                    pipeline.completeStream(player, response, scenario);
                } else {
                    // 非流式模式：通过管线输出
                    pipeline.send(player, response, scenario);
                }
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
                // 流式更新：使用场景对应的载体
                if (config.isStreamEnabled()) {
                    OutputChannel channel = pipeline.getChannelForScenario(scenario);
                    pipeline.updateStream(player, chunk, currentMessage, channel);
                }
            }

            @Override
            public void handleError(String errorMessage) {
                // 取消流式
                if (config.isStreamEnabled()) {
                    pipeline.cancelStream(player);
                }
                // 错误消息也通过管线输出
                pipeline.sendError(player, errorMessage);
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return config.isStreamEnabled();
            }
        };
    }

}
