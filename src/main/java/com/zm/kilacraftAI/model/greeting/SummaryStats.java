package com.zm.kilacraftAI.model.greeting;

import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.service.greeting.GreetingPromptBuilder;

import java.util.Collections;
import java.util.List;

/**
 * 问候摘要统计数据
 *
 * <p>包含从 {@code kca_player_profile} 和 {@code kca_server_event}
 * 表中提取的玩家里程碑数据，供 {@link GreetingPromptBuilder} 使用。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-06
 */
public record SummaryStats(
        /*
          累计游玩时间（ms）
         */
        long totalPlaytimeMs,

        /*
          累计登录次数
         */
        int loginCount,

        /*
          距首次登录天数
         */
        long daysSinceFirstLogin,

        /*
          上次游玩亮点事件（时间倒序，防御性拷贝为不可变列表）
         */
        List<ServerEvent> lastSessionHighlights,

        /*
          上次会话时长（ms）
         */
        long lastSessionDurationMs) {

    /**
     * 紧凑构造函数：对可变 List 做防御性拷贝，保证 record 不可变语义
     */
    public SummaryStats {
        lastSessionHighlights = List.copyOf(lastSessionHighlights);
    }

    /**
     * 空摘要（无数据时使用）
     */
    public static SummaryStats empty() {
        return new SummaryStats(0, 0, 0, Collections.emptyList(), 0);
    }
}
