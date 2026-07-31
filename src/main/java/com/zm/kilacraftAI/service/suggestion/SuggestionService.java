package com.zm.kilacraftAI.service.suggestion;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SuggestionConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐编排服务
 *
 * <p>仅命令模式（{@link ConversationSourceEnum#COMMAND}）触发推荐；连续对话模式（CHAT）
 * 核心体验是「像聊天一样自然」，每轮蹦推荐框会打断沉浸感。调用方须在
 * {@code saveToHistory} 之后调用本方法——此时 history 末尾已含本轮 user/assistant，
 * 推荐所需的「玩家问 + AI 答」完全由 history 提供，user_prompt 无需重复注入。</p>
 *
 * <p>去重不维护额外状态，依赖对话历史 + system_prompt 双层覆盖。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionService {

    private final KilacraftAI plugin;
    private final SuggestionConfigManager config;
    private final SuggestionPromptBuilder promptBuilder;
    private final SuggestionDisplayer displayer;

    public SuggestionService(KilacraftAI plugin) {
        this.plugin = plugin;
        this.config = plugin.getSuggestionConfigManager();
        this.promptBuilder = new SuggestionPromptBuilder(plugin, config);
        this.displayer = new SuggestionDisplayer(plugin, config);
    }

    /**
     * 在 AI 主回复发送完毕后调用，异步生成并展示推荐问题。
     *
     * @param player     目标玩家
     * @param history    对话历史队列（已含本轮，将被截断到最近 N 轮）
     * @param playerName 玩家名
     * @param scenario   当前输出场景
     * @param source     本次对话来源（CHAT 直接 return）
     */
    public void generateAsync(Player player, Deque<ConversationManager.Message> history, String playerName, OutputScenarioEnum scenario, ConversationSourceEnum source) {

        // 门控：全局开关、连续对话模式不触发、场景过滤、玩家级 opt-out
        // 每个门控加 debug：玩家问"为什么没推荐"时可从日志定位是哪个门控挡的
        if (!config.isEnabled()) {
            PluginLoggerUtil.debug("对话推荐", I18nService.tr("推荐跳过：全局开关关闭（玩家 {}）", playerName));
            return;
        }
        if (source == ConversationSourceEnum.CHAT) {
            PluginLoggerUtil.debug("对话推荐", I18nService.tr("推荐跳过：连续对话模式（玩家 {}）", playerName));
            return;
        }
        if (!config.isScenarioEnabled(scenario)) {
            PluginLoggerUtil.debug("对话推荐", I18nService.tr("推荐跳过：场景未启用（玩家 {}，场景 {}）", playerName, scenario));
            return;
        }
        if (!plugin.getSuggestionManager().isSuggestionEnabled(player.getUniqueId())) {
            PluginLoggerUtil.debug("对话推荐", I18nService.tr("推荐跳过：玩家已关闭推荐（玩家 {}）", playerName));
            return;
        }

        // 预算预检：SUGGESTION 档在熔断窗口内静默跳过
        LLMBudgetManager budget = plugin.getLlmOutputCoordinator() != null ? plugin.getLlmOutputCoordinator().getBudgetManager() : null;
        if (budget != null && !budget.tryAcquire(player.getUniqueId(), LLMBudgetManager.Priority.PASSIVE)) {
            PluginLoggerUtil.debug("对话推荐", I18nService.tr("LLM 预算熔断中，跳过推荐（玩家 {}）", playerName));
            return;
        }

        int maxCount = config.getMaxSuggestions();

        SuggestionPromptBuilder.SuggestionPrompt prompt = promptBuilder.build(maxCount, player);

        // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
        //PluginLoggerUtil.warn("对话推荐", "推荐提示词: system={}, user={}", prompt.systemPrompt(), prompt.userPrompt());

        // 静默 handler：GenericLLMProvider 在响应完成后会无条件调用 showResponse
        // （不受 isStreamOutputEnabled 守卫）。若用 PlayerResponseHandler，其 showResponse 会
        // 通过 AIResponsePipeline 把推荐原文发到玩家聊天框——泄露。推荐响应只从 future 拿，
        // showResponse 必须 no-op。getPlayerId 返回真实 UUID 使本次调用注册到 inFlightCalls，
        // 玩家下线时可被 cancelInFlight 取消。
        AIResponseHandler handler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return player.getUniqueId();
            }

            @Override
            public String getPlayerName() {
                return playerName;
            }

            @Override
            public void showResponse(String response) {
                // no-op：响应从 future 拿
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("LLM 调用错误: {}", errorMessage));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        // 截断 history 到最近 N 轮（复用 intent_history_count，与主对话意图识别同源）。
        // 浅拷贝快照（Message 字段不可变，浅拷贝=深拷贝语义）后再裁剪，避免影响调用方的 history。
        int maxMessages = plugin.getConfigManager().getAgentIntentHistoryCount() * 2;
        Deque<ConversationManager.Message> recentHistory = new ArrayDeque<>(history);
        while (recentHistory.size() > maxMessages) {
            recentHistory.pollFirst();
        }

        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        provider.processRequestWithCustomSystemPrompt(prompt.userPrompt(), playerName, recentHistory, handler, prompt.systemPrompt(), false, false, false, CacheCallTypeEnum.SUGGESTION).orTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS).thenAccept(rawResponse -> {
            if (rawResponse == null || rawResponse.isEmpty()) {
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("LLM 为 {} 返回了空的推荐话题", playerName));
                return;
            }
            List<String> suggestions = parseSuggestions(rawResponse, maxCount);
            if (suggestions.isEmpty()) {
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("LLM 为 {} 返回了空的推荐话题", playerName));
                return;
            }
            displayer.display(player, suggestions);
        }).exceptionally(ex -> {
            PluginLoggerUtil.warn("对话推荐", I18nService.tr("为 {} 生成推荐话题失败: {}", playerName, ex.getMessage()));
            return null;
        });
    }

    private List<String> parseSuggestions(String raw, int maxCount) {
        // LLM 错误响应以 §c 开头（错误信号协议），不当推荐
        if (LLMResponseUtil.isErrorResponse(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).limit(maxCount).collect(Collectors.toList());
    }
}
