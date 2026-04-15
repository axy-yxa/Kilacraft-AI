package com.zm.kilacraftAI.skills.afktask;

import lombok.Getter;

/**
 * 挂机任务类型
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
@Getter
public enum AFKTaskType {

    /**
     * 监视玩家上线
     */
    PLAYER_ONLINE_WATCH("PLAYER_ONLINE_WATCH", "监视玩家上线"),

    /**
     * 监视玩家下线
     */
    PLAYER_OFFLINE_WATCH("PLAYER_OFFLINE_WATCH", "监视玩家下线"),

    /**
     * 监视玩家死亡
     */
    PLAYER_DEATH_WATCH("PLAYER_DEATH_WATCH", "监视玩家死亡"),

    /**
     * 监视玩家传送
     */
    PLAYER_TELEPORT_WATCH("PLAYER_TELEPORT_WATCH", "监视玩家传送"),

    /**
     * 监视玩家等级变化
     */
    PLAYER_LEVEL_CHANGE_WATCH("PLAYER_LEVEL_CHANGE_WATCH", "监视玩家等级变化"),

    /**
     * 监视玩家切换世界
     */
    PLAYER_CHANGED_WORLD_WATCH("PLAYER_CHANGED_WORLD_WATCH", "监视玩家切换世界"),

    /**
     * 监视天气变化
     */
    WEATHER_CHANGE_WATCH("WEATHER_CHANGE_WATCH", "监视天气变化"),

    /**
     * 监视玩家进入床（睡觉）
     */
    PLAYER_BED_ENTER_WATCH("PLAYER_BED_ENTER_WATCH", "监视玩家进入床"),

    /**
     * 监视玩家离开床
     */
    PLAYER_BED_LEAVE_WATCH("PLAYER_BED_LEAVE_WATCH", "监视玩家离开床"),

    /**
     * 监视玩家重生
     */
    PLAYER_RESPAWN_WATCH("PLAYER_RESPAWN_WATCH", "监视玩家重生"),

    /**
     * 监视玩家物品损坏
     */
    PLAYER_ITEM_BREAK_WATCH("PLAYER_ITEM_BREAK_WATCH", "监视玩家物品损坏"),

    /**
     * 自定义任务（通过定时轮询检查任意Skill的返回值）
     */
    CUSTOM("CUSTOM", "自定义任务");

    private final String actionName;
    private final String description;

    AFKTaskType(String actionName, String description) {
        this.actionName = actionName;
        this.description = description;
    }

    /**
     * 根据 action 名称查找对应的任务类型
     *
     * @param actionName 动作名称
     * @return 对应的任务类型，找不到则返回 CUSTOM
     */
    public static AFKTaskType fromActionName(String actionName) {
        if (actionName == null) {
            return CUSTOM;
        }
        // 先精确匹配 actionName
        for (AFKTaskType type : values()) {
            if (type.actionName.equalsIgnoreCase(actionName)) {
                return type;
            }
        }
        // 再尝试匹配枚举名称（兼容 LLM 直接返回枚举名的情况）
        try {
            return valueOf(actionName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
