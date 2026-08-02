package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.GuardianConfigManager;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.guardian.EntityNameI18n;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.player.PlayerMetaCollector;
import com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 守护 LLM 动作：直接调 LLM Provider（不经 LLMOutputCoordinator 的技能分析路径），
 * 用守护专属系统提示词，输出自然语言提醒。
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class GuardianLlmAction {

    private static final String LOG_MODULE = "守护系统";
    private static final long LLM_TIMEOUT_SECONDS = 60L;

    private final GuardianConfigManager configManager;
    private final String scenarioDescription;

    public GuardianLlmAction(GuardianConfigManager configManager, String scenarioDescription) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.scenarioDescription = Objects.requireNonNull(scenarioDescription);
    }

    public CompletableFuture<Boolean> perform(GuardianContext ctx) {
        Player player = ctx.player();
        if (player == null || !player.isOnline()) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护动作跳过：玩家不在线"));
            return CompletableFuture.completedFuture(false);
        }

        KilacraftAI plugin = KilacraftAI.getInstance();
        UUID playerUuid = player.getUniqueId();

        // 全局开关关闭时不发声——reload 关闭期间在途动作也要抑制
        if (!configManager.isEnabled()) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护动作跳过：守护全局开关已关闭（玩家 {}）", player.getName()));
            return CompletableFuture.completedFuture(false);
        }
        // disable 下线后 in-flight action 深度防护：玩家刚关守护，仍可能收到延迟告警
        var gm = plugin.getGuardianManager();
        if (gm != null && !gm.isGuardianEnabled(playerUuid)) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护动作跳过：玩家 {} 的守护已停用", player.getName()));
            return CompletableFuture.completedFuture(false);
        }

        if (plugin.getLlmOutputCoordinator() != null) {
            LLMBudgetManager budget = plugin.getLlmOutputCoordinator().getBudgetManager();
            if (!budget.tryAcquire(playerUuid, LLMBudgetManager.Priority.PASSIVE)) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("LLM 预算熔断中，跳过守护输出（玩家 {}）", player.getName()));
                return CompletableFuture.completedFuture(false);
            }
        }

        // shutdown 窗口防护：子系统可能已在 onDisable 早阶段清理
        if (plugin.getLlmManager() == null || plugin.getLlmManager().getCurrentProvider() == null || plugin.getResponsePipeline() == null) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护动作跳过：LLM 子系统未就绪（玩家 {}）", player.getName()));
            return CompletableFuture.completedFuture(false);
        }

        String systemPrompt = PlayerMetaCollector.appendRuntimeContext(configManager.getGuardianSystemPrompt(), player);
        String userMessage = buildUserMessage(ctx);

        // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
        //PluginLoggerUtil.warn(LOG_MODULE, "守护提示词: system={}, user={}", systemPrompt, userMessage);

        PlayerResponseHandler handler = new PlayerResponseHandler(plugin, player, OutputScenarioEnum.GUARDIAN, null);

        boolean streamEnabled = plugin.getConfigManager().getOutputConfigManager().isStreamEnabled();
        if (streamEnabled) {
            OutputChannelEnum channel = plugin.getResponsePipeline().getChannelForScenario(OutputScenarioEnum.GUARDIAN);
            plugin.getResponsePipeline().startStream(player, channel, true);
        }

        return plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(userMessage, player.getName(), new ArrayDeque<>(), handler, systemPrompt, false, false, false, CacheCallTypeEnum.GUARDIAN).orTimeout(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS).thenApply(response -> {
            if (response == null || LLMResponseUtil.isErrorResponse(response)) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护 LLM 输出失败或为空（玩家 {}）", player.getName()));
                // 流式 UI 收尾由 handler.handleError 负责，此处不再重复 cancelStream
                return false;
            }
            if (player.isOnline()) {
                writeToConversation(plugin, playerUuid, response);
            }
            return true;
        }).exceptionally(throwable -> {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("守护 LLM 调用异常: {}", throwable.getMessage()));
            // 超时/异常：provider 不会回调 handleError，须手动收尾流式 UI + 中断在途 HTTP Call（防连接/线程泄漏）
            // 注意：超时可能在 shutdown 后触发（provider 可能已被 null 掉），复查避免 NPE
            cancelStreamIfActive(plugin, player, streamEnabled);
            var mgr = plugin.getLlmManager();
            if (mgr != null && mgr.getCurrentProvider() != null) {
                // cancelInFlight 按 playerUuid 取消该玩家全部在途调用（含普通聊天）——reload/quit 是低频用户操作，可接受
                mgr.getCurrentProvider().cancelInFlight(playerUuid);
            }
            return false;
        });
    }

    /**
     * 超时或异常时收尾流式输出，避免 UI 永久卡在「思考中」。
     */
    private static void cancelStreamIfActive(KilacraftAI plugin, Player player, boolean streamEnabled) {
        if (!streamEnabled) {
            return;
        }
        try {
            plugin.getResponsePipeline().cancelStream(player);
        } catch (Exception ignored) {
            // 收尾失败不影响状态机
        }
    }

    private String buildUserMessage(GuardianContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr(scenarioDescription));
        ctx.entityType().ifPresent(et -> sb.append(I18nService.tr("（{}）", EntityNameI18n.name(et))));
        Optional.ofNullable(ctx.triggerValue()).ifPresent(tv -> {
            double d = tv;
            String val = (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            sb.append(I18nService.tr("，当前数值：{}", val));
        });
        sb.append(I18nService.tr("。请用1-2句简短自然的话提醒玩家。"));
        return sb.toString();
    }

    private void writeToConversation(KilacraftAI plugin, UUID playerUuid, String response) {
        ConversationPersistenceService persistence = plugin.getPersistenceService();
        if (persistence != null) {
            persistence.submit(playerUuid, "assistant", response, "", ConversationSourceEnum.GUARDIAN.getValue());
        }
        ConversationManager cm = plugin.getConversationManager();
        if (cm != null) {
            cm.getOrCreateHistory(playerUuid).add(new ConversationManager.Message("assistant", response, ConversationSourceEnum.GUARDIAN.getValue()));
        }
    }
}
