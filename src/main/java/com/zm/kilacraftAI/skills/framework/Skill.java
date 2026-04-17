package com.zm.kilacraftAI.skills.framework;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Skill 基础接口
 *
 * <p>所有技能实现都必须实现此接口</p>
 *
 * <h3>安全规范：玩家数据隔离</h3>
 * <p>安全拦截器({@link SkillSecurityFilter})始终运行,不可跳过。</p>
 * <p>拦截器扫描entities中所有Value,如果某个Value匹配在线玩家名且不是当前玩家自己,
 * 则自动消毒(替换为当前玩家名)。这是非合作式安全机制,无法通过Skill声明绕过。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public interface Skill {

    /**
     * 获取技能名称（唯一标识）
     *
     * @return 技能名称
     */
    String getName();

    /**
     * 获取技能描述（用于 LLM 意图识别）
     *
     * @return 技能描述
     */
    String getDescription();

    /**
     * 获取动作列表（如果有多个动作）
     * 默认返回空列表（表示只有一个动作）
     *
     * @return Map<String, String> key=动作名称，value=动作描述
     */
    default Map<String, String> getActions() {
        return Collections.emptyMap();
    }

    /**
     * 获取额外提示信息（可选）
     * 用于提供使用示例、注意事项等
     *
     * @return 提示列表
     */
    default List<String> getHints() {
        return Collections.emptyList();
    }

    /**
     * 执行技能
     *
     * @param context 执行上下文
     * @return 执行结果（异步）
     */
    CompletableFuture<SkillResult> execute(SkillContext context);

    /**
     * 检查技能是否可用
     *
     * @param context 执行上下文
     * @return true=可用，false=不可用
     */
    default boolean isAvailable(SkillContext context) {
        return true;
    }
}
