package com.zm.kilacraftAI.common.enums;

/**
 * AI 响应输出场景枚举
 *
 * <p>定义所有 AI 消息输出的业务场景，用于场景级载体配置。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public enum OutputScenarioEnum {

    /**
     * 普通 AI 对话
     */
    NORMAL_CHAT,

    /**
     * 单意图技能执行结果
     */
    SKILL_RESULT,

    /**
     * 多步骤任务执行结果
     */
    TASK_RESULT,

    /**
     * AI 登录问候
     */
    GREETING,

    /**
     * 错误消息
     */
    ERROR,

    /**
     * 对话推荐
     */
    SUGGESTION
}
