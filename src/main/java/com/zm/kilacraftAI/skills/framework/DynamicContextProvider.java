package com.zm.kilacraftAI.skills.framework;

import org.bukkit.entity.Player;

/**
 * 可选能力接口：向 Phase 2 意图识别提示词追加运行时动态上下文。
 *
 * <p>这是 {@link Skill} 之上的<b>可选增强</b>，只有确实需要注入运行时动态内容
 * （如可用选项列表）的 skill 实现它。意图识别器按 {@code instanceof} 判断是否调用，
 * 未实现的 skill 不受影响。</p>
 *
 * <p>典型场景：监听技能（WatchSkill）注入可用 probe 列表——probe 由 Java 代码定义、
 * 无法写进静态 yml 描述，需运行时拼装。普通 skill 不需要实现本接口。</p>
 *
 * <p><b>安全提示</b>：返回的文本会原样拼进 LLM 提示词，实现方须确保内容可信，
 * 不应包含来自玩家的未消毒输入。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-23
 */
public interface DynamicContextProvider {

    /**
     * 运行时动态上下文，追加在该技能描述之后的 Phase 2 提示词中。
     *
     * @param player 当前调用方玩家，用于按权限过滤动态内容（如可监听列表）；
     *               调用方保证非 null（意图识别/建议构建均在玩家上下文中触发）
     * @return 动态上下文文本；空字符串或 null 表示无附加内容
     */
    String getDynamicContext(Player player);
}
