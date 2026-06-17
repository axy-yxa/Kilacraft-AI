package com.zm.kilacraftAI.skills.framework;

/**
 * Skill 执行结果的结构化状态（仅用于呈现层打标，不驱动控制流）。
 *
 * <p>控制流（技能执行、多步骤依赖级联、统计）继续基于 {@link SkillResult#isSuccess()} 二分判断；
 * 本枚举仅由归一化层读取，统一输出 {@code [STATUS]} marker 给 LLM，避免各 skill 自行手写前缀导致裸奔/双标。</p>
 *
 * <ul>
 *   <li>{@link #SUCCESS} / {@link #FAILURE} / {@link #NEED_INFO}：可由 skill（含第三方 SPI）产生</li>
 *   <li>{@link #SKIPPED}：仅供框架内部使用（多步骤依赖未满足 / 参数解析失败），skill 不得产生</li>
 * </ul>
 *
 * <p>枚举名即 marker 文本（ASCII、本地化无关），消除历史上"中文 bracket 嗅探在英文环境失配"的隐患。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-16
 */
public enum SkillStatus {
    /**
     * 执行成功
     */
    SUCCESS,
    /**
     * 硬失败：无法继续（权限/余额/未找到/参数非法/插件缺失等）
     */
    FAILURE,
    /**
     * 软失败：需玩家补全参数或二次确认后才能继续
     */
    NEED_INFO,
    /**
     * 框架内部：多步骤依赖未满足 / 参数解析失败（skill 不得产生）
     */
    SKIPPED;

    /**
     * 输出给 LLM 的 bracket marker，如 {@code [SUCCESS]}。
     *
     * @return 形如 "[STATUS]" 的 marker 字符串
     */
    public String prefix() {
        return "[" + name() + "]";
    }
}
