package com.zm.kilacraftAI.skills.framework.resume;

import lombok.Getter;

import java.util.Map;

/**
 * 待确认操作的恢复分类结果（框架内部）。由分类器产出，驱动恢复动作。
 *
 * <ul>
 *   <li>{@link Type#CONFIRM} —— 肯定意向，置 isConfirmed=true 复用执行。</li>
 *   <li>{@link Type#RESPOND} —— 给新值或改值，合并 entities 复用执行。</li>
 *   <li>{@link Type#CANCEL} —— 放弃，清槽位。</li>
 * </ul>
 *
 * <p>无关回复分类返回 null，由调用方落回正常意图识别。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-17
 */
@Getter
public final class PendingAction {

    public enum Type {CONFIRM, RESPOND, CANCEL}

    private final Type type;
    /**
     * 仅 {@link Type#RESPOND} 有值（玩家本轮新给/改的字段）；CONFIRM/CANCEL 为 null
     */
    private final Map<String, String> entities;

    public PendingAction(Type type, Map<String, String> entities) {
        this.type = type;
        this.entities = entities;
    }

    public static PendingAction confirm() {
        return new PendingAction(Type.CONFIRM, null);
    }

    public static PendingAction respond(Map<String, String> entities) {
        return new PendingAction(Type.RESPOND, entities);
    }

    public static PendingAction cancel() {
        return new PendingAction(Type.CANCEL, null);
    }
}
