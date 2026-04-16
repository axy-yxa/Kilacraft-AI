package com.zm.kilacraftAI.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.util.MessageUtil;
import lombok.Getter;
import org.bukkit.entity.Player;

/**
 * AI 响应输出管线（核心组件）
 *
 * <p>统一所有 AI 回复的输出入口，实现：</p>
 * <ul>
 *   <li>阶段1：格式化 - Markdown 转 Minecraft 格式 + 前缀</li>
 *   <li>阶段2：路由 - 根据场景配置选择输出载体</li>
 *   <li>阶段3：输出 - 通过 MessageDispatcher 分发到具体载体</li>
 * </ul>
 *
 * <h3>覆盖的输出点（重构前）：</h3>
 * <ol>
 *   <li>PlayerResponseHandler.showResponse() - 普通对话</li>
 *   <li>AIRequestHandler.ctx.sendResponse - 多步骤任务</li>
 *   <li>LLMAnalysisService 匿名 Handler - 单意图二次分析</li>
 *   <li>AFKTask.notifyPlayer() - 挂机任务通知</li>
 *   <li>AFKTask.notifyCallbackResult() - 挂机任务回调</li>
 * </ol>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>向后兼容：默认 CHAT 载体，行为与原有代码完全一致</li>
 *   <li>单一职责：只负责输出，不关心业务逻辑</li>
 *   <li>配置驱动：输出载体由 config.yml 决定，代码无硬编码</li>
 *   <li>易于扩展：新增载体只需改 MessageDispatcher</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class AIResponsePipeline {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;
    /**
     * 消息分发器（用于高级场景）
     */
    @Getter
    private final MessageDispatcher dispatcher;

    public AIResponsePipeline(KilacraftAI plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.dispatcher = new MessageDispatcher(plugin);
    }

    /**
     * 发送 AI 响应到玩家（统一入口）
     *
     * @param player     目标玩家
     * @param rawMessage 原始消息（未格式化，可能包含 Markdown）
     * @param scenario   输出场景（用于场景级载体配置）
     */
    public void send(Player player, String rawMessage, OutputScenario scenario) {
        if (player == null || rawMessage == null) {
            return;
        }

        // 阶段1: 格式化
        String formatted = formatMessage(rawMessage, true);

        // 阶段2: 路由（选择载体）
        OutputChannel channel = resolveChannel(scenario);

        // 阶段3: 输出
        dispatcher.dispatch(player, formatted, channel);
    }

    /**
     * 发送错误消息到玩家
     *
     * @param player       目标玩家
     * @param errorMessage 错误消息（已格式化）
     */
    public void sendError(Player player, String errorMessage) {
        if (player == null || errorMessage == null) {
            return;
        }

        // 错误消息使用 ERROR 场景配置
        OutputChannel channel = resolveChannel(OutputScenario.ERROR);
        dispatcher.dispatch(player, errorMessage, channel);
    }

    /**
     * 公屏广播（关键词触发 public_reply=true 时使用）
     *
     * <p>固定使用 CHAT 载体 + 前缀，因为 ActionBar/BossBar/Title 都是玩家私有的</p>
     *
     * @param rawMessage 原始消息
     * @param scenario   输出场景
     */
    public void broadcast(String rawMessage, OutputScenario scenario) {
        if (rawMessage == null) {
            return;
        }

        // 阶段1: 格式化（公屏广播始终应用前缀）
        String formatted = formatMessage(rawMessage, true);

        // 阶段2: 固定使用 CHAT 载体
        OutputChannel channel = OutputChannel.CHAT;

        // 阶段3: 输出到所有在线玩家
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            dispatcher.dispatch(onlinePlayer, formatted, channel);
        }
    }

    /**
     * 格式化消息
     *
     * @param rawMessage  原始消息
     * @param applyPrefix 是否应用 AI 前缀
     * @return 格式化后的 Minecraft 消息
     */
    private String formatMessage(String rawMessage, boolean applyPrefix) {
        String result = MessageUtil.convertMarkdownToMinecraft(rawMessage);
        if (applyPrefix) {
            result = MessageUtil.getAIPrefix() + result;
        }
        return result;
    }

    /**
     * 解析输出载体
     *
     * <p>路由逻辑：</p>
     * <ol>
     *   <li>优先使用场景级配置（scenarios.xxx）</li>
     *   <li>场景未配置时回退到全局默认（default_channel）</li>
     * </ol>
     *
     * @param scenario 输出场景
     * @return 输出载体
     */
    private OutputChannel resolveChannel(OutputScenario scenario) {
        return config.getChannelForScenario(scenario);
    }

    /**
     * 清理资源（插件卸载时调用）
     */
    public void cleanup() {
        dispatcher.getBossBarManager().cleanup();
        dispatcher.getScoreboardManager().cleanup();
    }
}
