package com.zm.kilacraftAI.service.guardian;

/**
 * 守护告警的内部分类（D4：模式合并为自动选择——保镖/管家/陪聊降级为此枚举）。
 * 用于 {@link GuardianCooldownHub} 的分类冷却 / 静音列表 / 画像相关性过滤。
 *
 * <p>{@link #GENERAL} 为未分类默认值——仅受全局冷却约束，不受分类冷却，
 * 避免未显式归类的 monitor 被过度压制。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum AlertCategory {
    /** 未分类（默认）：仅受全局冷却约束，不参与分类冷却/静音/相关性。 */
    GENERAL,
    /** 保镖/危险：背后威胁、溺水、火灾等需即时响应的状态。 */
    DANGER,
    /** 管家/资源：库存低、耐久耗损、食物不足等维持类提醒。 */
    RESOURCE,
    /** 目标驱动：凑材料、打怪计数等玩家设定的进度目标。 */
    GOAL,
    /** 陪聊（opt-in）：模糊信号下的 LLM 主动提议。 */
    COMPANION
}
