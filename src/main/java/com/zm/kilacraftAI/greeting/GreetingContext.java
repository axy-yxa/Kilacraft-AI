package com.zm.kilacraftAI.greeting;

import com.zm.kilacraftAI.event.ServerEvent;
import com.zm.kilacraftAI.profile.PlayerProfile;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 问候上下文数据
 *
 * <p>封装生成问候语所需的全部信息，由 {@link LoginGreetingHandler} 构建，
 * 传递给 {@link GreetingPromptBuilder} 生成 LLM 提示词。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-01
 */
@Getter
@Builder
public class GreetingContext {

    /**
     * 目标玩家
     */
    private final org.bukkit.entity.Player player;

    /**
     * 玩家画像（可为 null，表示首次登录）
     */
    private final PlayerProfile profile;

    /**
     * 是否首次登录
     */
    private final boolean firstLogin;

    /**
     * 离线时长（ms），首次登录为 0
     */
    private final long offlineDurationMs;

    /**
     * 离线期间的事件列表（可为空）
     */
    private final List<ServerEvent> offlineEvents;

    /**
     * 离线期间好友的动态事件列表（可为空，playerName 已通过 JOIN 填充）
     */
    private final List<ServerEvent> friendEvents;

    /**
     * 摘要统计数据（可为 null）
     */
    private final SummaryStats summaryStats;

    /**
     * 目前在线的好友名称列表（可为空）
     */
    private final List<String> onlineFriends;

    /**
     * 服务器信息（来自知识库，可为 null）
     */
    private final String serverInfo;
}
