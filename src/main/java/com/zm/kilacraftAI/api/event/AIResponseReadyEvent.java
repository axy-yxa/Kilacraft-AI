package com.zm.kilacraftAI.api.event;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * AI 回复就绪事件
 *
 * <p>当 AI 完成回复时触发此事件，第三方插件可以监听此事件获取 AI 回复内容。</p>
 *
 * <p><b>使用示例（完全零耦合，通过反射）：</b></p>
 * <pre>{@code
 * @EventHandler
 * public void onAIResponse(org.bukkit.event.Event event) {
 *     try {
 *         Class<?> eventClass = event.getClass();
 *         if (!eventClass.getName().equals("com.zm.kilacraftAI.api.event.AIResponseReadyEvent")) {
 *             return;
 *         }
 *
 *         String playerName = (String) eventClass.getMethod("getPlayerName").invoke(event);
 *         String response = (String) eventClass.getMethod("getResponse").invoke(event);
 *         String personality = (String) eventClass.getMethod("getPersonality").invoke(event);
 *
 *         getLogger().info("收到 AI 回复: " + response);
 *     } catch (Exception e) {
 *         e.printStackTrace();
 *     }
 * }
 * }</pre>
 *
 * @author Zm_Mmm
 * @since 2026-03-24
 */
@Getter
public class AIResponseReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * 玩家 UUID
     */
    private final UUID playerId;
    /**
     * 玩家名称
     */
    private final String playerName;
    /**
     * 人格类型
     */
    private final String personality;
    /**
     * AI 回复内容
     */
    private final String response;

    /**
     * 构造 AI 回复就绪事件
     *
     * @param playerId    玩家 UUID
     * @param playerName  玩家名称
     * @param personality 人格类型
     * @param response    AI 回复内容
     */
    public AIResponseReadyEvent(UUID playerId, String playerName, String personality, String response) {
        super(true); // 异步事件
        this.playerId = playerId;
        this.playerName = playerName;
        this.personality = personality;
        this.response = response;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
