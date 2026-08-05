package com.zm.kilacraftAI.common.enums;

import lombok.Getter;

/**
 * 对话来源枚举
 *
 * <p>定义所有对话历史的来源类型，用于 {@code kca_conversation} 表的 {@code source} 字段。</p>
 * <p>不同来源的对话在加载时通过 source 过滤隔离，互不污染。</p>
 *
 * <p><b>字段长度约束：getValue().length() 不得超过 16 字符</b>（对应 DDL {@code VARCHAR(16)}）。
 * 新增枚举项时必须检查长度。</p>
 *
 * <ul>
 *     <li>{@link #CHAT} — 连续对话模式 / 关键词触发</li>
 *     <li>{@link #COMMAND} — /kila 命令触发</li>
 *     <li>{@link #PLUGIN} — 插件命令 /kila plugins（带人格隔离）</li>
 *     <li>{@link #GREETING} — 登录问候（写入 DB 与内存历史；DB 加载时不回读，仅在当前会话内存中由 HistoryUtil 渲染来源标签）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
@Getter
public enum ConversationSourceEnum {

    /**
     * 聊天触发（连续对话模式 / 关键词触发）
     */
    CHAT("chat"),

    /**
     * 命令触发（/kila 消息）
     */
    COMMAND("command"),

    /**
     * 插件命令（/kila plugins 人格，带人格隔离）
     */
    PLUGIN("plugin"),

    /**
     * 登录问候（写入 DB 与内存历史；DB 加载时不回读，仅当前会话内存有效）
     */
    GREETING("greeting");

    /**
     * 字段值
     */
    private final String value;

    ConversationSourceEnum(String value) {
        this.value = value;
    }

}
