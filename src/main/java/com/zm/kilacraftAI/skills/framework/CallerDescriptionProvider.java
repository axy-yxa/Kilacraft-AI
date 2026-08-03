package com.zm.kilacraftAI.skills.framework;

import org.bukkit.entity.Player;

/**
 * 可选能力接口：按调用者（玩家）权限定制 Phase 1 意图识别的 Skill 描述。
 *
 * <p>这是 {@link Skill} 之上的<b>可选增强</b>，仅当 Skill 的 description 需要随调用者
 * 权限变化时实现（如 CommandSkill 注入当前玩家可见的命令摘要）。意图识别器按
 * {@code instanceof} 判断是否调用，未实现的 Skill 不受影响（走无参 {@link Skill#getDescription()}）。</p>
 *
 * <p>与 {@link DynamicContextProvider} 的区分：本接口作用于 Phase 1（skill 分类阶段的
 * description 摘要），DCP 作用于 Phase 2（选中后的完整上下文）。两者都是内部接口，不进 SPI 白名单。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public interface CallerDescriptionProvider {

    /**
     * 按调用者权限定制的 Skill 描述。
     *
     * @param caller 当前调用方玩家，用于按权限过滤描述内容；null 表示控制台（视为拥有所有权限）
     * @return 描述文本
     */
    String getCallerDescription(Player caller);
}
