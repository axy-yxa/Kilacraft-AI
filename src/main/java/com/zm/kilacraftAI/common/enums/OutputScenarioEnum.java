package com.zm.kilacraftAI.common.enums;

/**
 * AI 响应输出场景枚举
 *
 * <p>定义所有 AI 消息输出的业务场景，用于场景级载体配置。</p>
 *
 * <h3>场景说明：</h3>
 * <ul>
 *   <li>NORMAL_CHAT - 普通 AI 对话（无技能调用）</li>
 *   <li>SKILL_RESULT - 单意图技能执行结果（含 LLM 二次分析）</li>
 *   <li>TASK_RESULT - 多步骤任务执行结果（含 LLM 二次分析）</li>
 *   <li>AFK_CALLBACK - 挂机任务回调通知</li>
 *   <li>ERROR - 错误消息</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public enum OutputScenarioEnum {

    /**
     * 普通 AI 对话
     * <p>来源：handleNormalAIRequest() → GenericLLMProvider → PlayerResponseHandler</p>
     */
    NORMAL_CHAT,

    /**
     * 单意图技能执行结果
     * <p>来源：handleSkillIntent() → SkillManager → LLMAnalysisService.analyzeResultWithHandler()</p>
     */
    SKILL_RESULT,

    /**
     * 多步骤任务执行结果
     * <p>来源：handleTaskPlan() → TaskExecutor.executeTask()</p>
     */
    TASK_RESULT,

    /**
     * 挂机任务回调通知
     * <p>来源：AFKTask.notifyPlayer() / notifyCallbackResult()</p>
     */
    AFK_CALLBACK,

    /**
     * AI 登录问候
     * <p>来源：LoginGreetingHandler</p>
     */
    GREETING,

    /**
     * 错误消息
     * <p>来源：所有异常处理路径</p>
     */
    ERROR
}
