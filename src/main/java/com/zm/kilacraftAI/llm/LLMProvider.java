package com.zm.kilacraftAI.llm;

import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 提供商统一接口
 * <p>
 * 支持多个 LLM 提供商的统一接入和无缝切换
 *
 * @author Zm_Mmm
 * @since 2026-04-03
 */
public interface LLMProvider {

    /**
     * 处理 AI 请求。
     * <p>
     * system 消息由 {@code staticSystemPrompt}（可含 {player} 占位符）拼装，保持稳定；
     * 玩家画像、实时元数据、当前时间等会变的内容由本方法注入到 user 消息。
     *
     * @param userMessage              实际用户查询
     * @param player                   触发请求的玩家；非 null 时注入画像/元数据/时间到 user 消息，
     *                                 并以其名替换 system 中的 {player}；null 表示无玩家上下文
     * @param history                  历史对话记录，作为 messages[1..N] 夹在 system 与 user 之间
     * @param responseHandler          响应处理器
     * @param staticSystemPrompt       系统提示词（人格/模板，可含 {player} 占位符）
     * @param enableKnowledgeRetrieval 是否启用知识检索（命中时拼到 user 消息、紧贴查询之前）
     * @param enableDebugLog           是否启用调试日志
     * @param enableJsonOutput         是否启用 JSON 输出
     * @param cacheCallTypeEnum        调用类型（用于缓存命中率统计，可为 null 表示不统计）
     * @return 完整的 AI 响应
     */
    CompletableFuture<String> processRequestWithCustomSystemPrompt(String userMessage, @Nullable Player player, Deque<ConversationManager.Message> history, AIResponseHandler responseHandler, String staticSystemPrompt, boolean enableKnowledgeRetrieval, boolean enableDebugLog, boolean enableJsonOutput, @Nullable CacheCallTypeEnum cacheCallTypeEnum);

    /**
     * 刷新配置缓存
     */
    void refreshConfigCache();

    /**
     * 关闭资源（用于插件卸载时）
     */
    void shutdown();

    /**
     * 取消指定玩家所有在途 LLM 调用。玩家下线时调用，避免 token/IO 线程浪费与离线后错乱回调。
     *
     * @param playerId 玩家 UUID
     */
    default void cancelInFlight(UUID playerId) {
    }
}
