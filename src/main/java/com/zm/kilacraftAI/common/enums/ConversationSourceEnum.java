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
 *     <li>{@link #COMMAND} — /kilacraft 命令触发</li>
 *     <li>{@link #PLUGIN} — 插件命令 /kilacraft plugins（带人格隔离）</li>
 *     <li>{@link #CONSOLE} — 控制台对话（不持久化）</li>
 *     <li>{@link #GREETING} — 登录问候（Phase 4，仅写 DB 不加载到内存）</li>
 *     <li>{@link #AFK_CALLBACK} — 挂机任务回调（仅写 DB 不加载到内存）</li>
 * </ul>
 *
 * @author Zm_Mmm
 */
@Getter
public enum ConversationSourceEnum {

    /**
     * 聊天触发（连续对话模式 / 关键词触发）
     */
    CHAT("chat"),

    /**
     * 命令触发（/kilacraft 消息）
     */
    COMMAND("command"),

    /**
     * 插件命令（/kilacraft plugins 人格，带人格隔离）
     */
    PLUGIN("plugin"),

    /**
     * 控制台对话（不持久化）
     */
    CONSOLE("console"),

    /**
     * 登录问候（仅写 DB，不加载到内存历史）
     */
    GREETING("greeting"),

    /**
     * 挂机任务回调（仅写 DB，不加载到内存历史）
     */
    AFK_CALLBACK("afk_callback");

    /**
     * 字段值
     */
    private final String value;

    ConversationSourceEnum(String value) {
        this.value = value;
    }

}
