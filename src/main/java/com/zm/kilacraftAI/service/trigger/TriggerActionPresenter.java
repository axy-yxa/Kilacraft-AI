package com.zm.kilacraftAI.service.trigger;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.common.util.SilentCallHandlerFactory;
import com.zm.kilacraftAI.config.WatchConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.suggestion.SuggestionDisplayer;
import com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 触发指令展示器：监听/订阅触发后，把玩家创建时表达的后续动作意图（note/intent）转换为
 * 可执行动作请求，以可点击推荐项展示（点击即 /ai 发起）。
 *
 * <p>与对话推荐系统构成「通用内核双调用者」：本类是被动确定性输出调用者（事件已发生，
 * 转换描述视角），推荐系统是主动预测调用者；两者共用 {@link SuggestionDisplayer} 展示内核
 * 与 {@link SilentCallHandlerFactory} 静默调用模式。点击发送完整指令，意图识别无需历史/注入上下文。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-08
 */
public class TriggerActionPresenter {

    private static final String LOG_MODULE = "触发指令";

    private final KilacraftAI plugin;
    private final SuggestionDisplayer displayer;

    public TriggerActionPresenter(KilacraftAI plugin, SuggestionDisplayer displayer) {
        this.plugin = plugin;
        this.displayer = displayer;
    }

    /**
     * 触发通知后展示「可执行操作」点击项。
     *
     * @param player      目标玩家（须在线，调用方保证）
     * @param triggerDesc 事件描述
     * @param intentText  玩家后续动作意图（note/intent），为空则跳过
     */
    public void present(Player player, String triggerDesc, String intentText) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (triggerDesc == null || triggerDesc.isBlank() || intentText == null || intentText.isBlank()) {
            return;
        }
        WatchConfigManager watchConfig = plugin.getWatchConfigManager();
        if (watchConfig == null) {
            return;
        }

        // 被动输出：预算熔断窗口内静默跳过（与对话推荐同档）
        if (plugin.getLlmOutputCoordinator() != null) {
            var budget = plugin.getLlmOutputCoordinator().getBudgetManager();
            if (budget != null && !budget.tryAcquire(player.getUniqueId(), LLMBudgetManager.Priority.PASSIVE)) {
                PluginLoggerUtil.debug(LOG_MODULE, "LLM 预算熔断中，跳过触发指令构造（玩家 {}）", player.getName());
                return;
            }
        }

        // player 传 null：无玩家上下文（不注入画像/实时状态，输入只有事件+意图）；
        // cacheCallTypeEnum 传 null：不参与缓存统计（token 极少、频率极低）。
        // inFlight 注册与预算记账经 handler 的真实 UUID 仍生效。
        String userMessage = I18nService.tr("事件：{}", triggerDesc) + "\n" + I18nService.tr("玩家意图：{}", intentText);
        AIResponseHandler handler = SilentCallHandlerFactory.silent(player.getUniqueId(), player.getName(), LOG_MODULE);
        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        if (provider == null) {
            return;
        }

        provider.processRequestWithCustomSystemPrompt(userMessage, null, null, handler, watchConfig.getTriggerActionPrompt(), false, true, false, null).orTimeout(15, TimeUnit.SECONDS).thenAccept(instruction -> {
            if (instruction == null || instruction.isBlank()) {
                PluginLoggerUtil.debug(LOG_MODULE, "触发指令构造返回空（玩家 {}），跳过展示", player.getName());
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            // 取首行（多行输出会破坏点击项展示与 /ai 命令）
            String action = cleanInstruction(instruction);
            if (action == null) {
                return;
            }
            displayer.display(player, List.of(action), "§7" + I18nService.tr("可执行操作") + "：", I18nService.tr("点击执行"), "§7 | ");
        }).exceptionally(throwable -> {
            PluginLoggerUtil.debug(LOG_MODULE, "触发指令构造失败（玩家 {}）: {}", player.getName(), throwable.getMessage());
            return null;
        });
    }

    /**
     * 指令清洗：取首行去空白；清洗后为空返回 null。
     */
    static String cleanInstruction(String instruction) {
        if (instruction == null) {
            return null;
        }
        String action = instruction.trim();
        int newline = action.indexOf('\n');
        if (newline >= 0) {
            action = action.substring(0, newline).trim();
        }
        return action.isEmpty() ? null : action;
    }
}
