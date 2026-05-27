package com.zm.kilacraftAI.model.greeting;

import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.greeting.GreetingPromptBuilder;
import com.zm.kilacraftAI.service.greeting.LoginGreetingHandler;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

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
     * 目前在线的好友状态列表（可为空）
     * 每项包含好友名、所在世界名、当前会话时长（分钟）
     */
    private final List<FriendStatus> onlineFriends;

    /**
     * 离线好友状态列表（可为空）
     * 每项包含好友名、world为空、sessionMinutes 为距下线的分钟数
     */
    private final List<FriendStatus> offlineFriends;

    /**
     * 离线期间全服事件总数
     */
    private final int globalEventCount;

    /**
     * 离线期间好友登录次数 玩家名 → 登录次数
     */
    private final Map<String, Integer> friendLoginCounts;

    /**
     * 服务器信息（来自知识库，可为 null）
     */
    private final String serverInfo;

    /**
     * Bukkit 原版统计数据（主线程采集，可为 null 表示采集失败）
     */
    private final PlayerVanillaStats vanillaStats;

    /**
     * 离线期间的系统健康告警事件（仅对有 kilacraft.admin.health 权限的管理员填充）
     */
    private final List<ServerEvent> healthAlerts;
}
