package com.zm.kilacraftAI.common.enums;

/**
 * LLM 消息角色枚举
 *
 * <p>统一管理 OpenAI 兼容 API 中 messages 数组的 role 字段值。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-18
 */
public enum MessageRoleEnum {

    SYSTEM("system"), USER("user"), ASSISTANT("assistant");

    private final String value;

    MessageRoleEnum(String value) {
        this.value = value;
    }

    /**
     * 获取 API 请求中使用的 role 字符串值
     */
    public String value() {
        return value;
    }
}
