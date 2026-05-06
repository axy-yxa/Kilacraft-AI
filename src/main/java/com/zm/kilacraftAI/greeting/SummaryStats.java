package com.zm.kilacraftAI.greeting;

import com.zm.kilacraftAI.event.ServerEvent;

import java.util.Collections;
import java.util.List;

/**
 * 问候摘要统计数据（分类三：其他摘要）
 *
 * <p>包含从 {@code kca_player_profile} 和 {@code kca_server_event}
 * 表中提取的玩家里程碑数据，供 {@link GreetingPromptBuilder} 使用。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-06
 */
public record SummaryStats(
        /**
         * 累计游玩时间（ms）
         */
        long totalPlaytimeMs,

        /**
         * 累计登录次数
         */
        int loginCount,

        /**
         * 距首次登录天数
         */
        long daysSinceFirstLogin,

        /**
         * 上次游玩亮点事件（时间倒序，防御性拷贝为不可变列表）
         */
        List<ServerEvent> lastSessionHighlights
) {

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
        return new SummaryStats(0, 0, 0, Collections.emptyList());
    }
}
