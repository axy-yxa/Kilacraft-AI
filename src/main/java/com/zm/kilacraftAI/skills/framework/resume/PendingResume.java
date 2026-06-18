package com.zm.kilacraftAI.skills.framework.resume;

import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * 待确认续体值对象（框架内部，不进 SPI Jar）。
 *
 * <p>needInfo 暂停时快照已收集的参数，供玩家下轮恢复执行。只存参数快照，不存实时世界态
 * （余额等由 Skill 现场重读）。不可变，刷新由 manager 重建实例。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-17
 */
@Getter
public final class PendingResume {

    /**
     * 玩家 UUID（槽位键）
     */
    private final UUID playerId;
    /**
     * 要恢复的技能名
     */
    private final String skillName;
    /**
     * 要恢复的动作
     */
    private final String action;
    /**
     * 累积参数快照（已消毒、占位符已解析）
     */
    private final Map<String, String> entities;
    /**
     * 原始 needInfo 文案（下轮分类注入用，非 LLM 改写版）
     */
    private final String message;
    /**
     * 捕获时间戳(ms)
     */
    private final long capturedAt;
    /**
     * 过期时间戳(ms)
     */
    private final long expiresAt;
    /**
     * 已恢复次数（防死循环计数，0=首次捕获）
     */
    private final int round;

    public PendingResume(UUID playerId, String skillName, String action, Map<String, String> entities, String message, long capturedAt, long expiresAt, int round) {
        this.playerId = playerId;
        this.skillName = skillName;
        this.action = action;
        this.entities = entities;
        this.message = message;
        this.capturedAt = capturedAt;
        this.expiresAt = expiresAt;
        this.round = round;
    }

    /**
     * 是否已过期。
     *
     * @param now 当前时间戳(ms)
     * @return now &gt;= expiresAt 时为 true
     */
    public boolean isExpired(long now) {
        return now >= expiresAt;
    }
}
