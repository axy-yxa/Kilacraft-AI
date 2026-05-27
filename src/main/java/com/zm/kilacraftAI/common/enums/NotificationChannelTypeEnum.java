package com.zm.kilacraftAI.common.enums;

import lombok.Getter;

/**
 * 外部通知渠道类型枚举
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
@Getter
public enum NotificationChannelTypeEnum {

    DISCORD("discord", "Discord"), DINGTALK("dingtalk", "钉钉");

    private final String type;
    private final String displayName;

    NotificationChannelTypeEnum(String type, String displayName) {
        this.type = type;
        this.displayName = displayName;
    }

    /**
     * 根据类型字符串查找枚举值
     *
     * @param type 类型字符串（不区分大小写）
     * @return 匹配的枚举值，未匹配时返回 null
     */
    public static NotificationChannelTypeEnum fromType(String type) {
        for (NotificationChannelTypeEnum t : values()) {
            if (t.type.equalsIgnoreCase(type)) return t;
        }
        return null;
    }
}
