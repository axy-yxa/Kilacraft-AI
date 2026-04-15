package com.zm.kilacraftAI.enums;

/**
 * AI 响应输出载体枚举
 *
 * <p>定义所有支持的 AI 消息输出位置。</p>
 *
 * <h3>载体特性对比：</h3>
 * <ul>
 *   <li>CHAT - 聊天栏，支持多行，历史记录，持久可见</li>
 *   <li>ACTION_BAR - 物品栏上方，单行，自动覆盖更新，适合短文本</li>
 *   <li>BOSS_BAR - 顶部 BossBar，支持颜色/样式，可驻留，适合中等长度</li>
 *   <li>TITLE - 屏幕中央标题，定时消失，不适合长文本</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public enum OutputChannel {

    /**
     * 聊天栏（默认载体）
     * <p>特性：支持多行、历史记录、持久可见</p>
     * <p>适用场景：所有类型的 AI 回复</p>
     */
    CHAT,

    /**
     * 物品栏上方 ActionBar
     * <p>特性：单行、自动覆盖更新、不占聊天空间</p>
     * <p>适用场景：短文本回复（&lt;50字符）、流式输出</p>
     */
    ACTION_BAR,

    /**
     * 顶部 BossBar
     * <p>特性：支持颜色/样式、可驻留、视觉突出</p>
     * <p>适用场景：中等长度回复（50-200字符）、需要强调的消息</p>
     */
    BOSS_BAR,

    /**
     * 屏幕中央 Title
     * <p>特性：全屏居中、定时消失、不适合频繁更新</p>
     * <p>适用场景：极短提示（&lt;30字符）、重要通知</p>
     */
    TITLE
}
