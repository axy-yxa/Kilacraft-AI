package com.zm.kilacraftAI.skills.framework;

import org.bukkit.entity.Player;

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
     * <p><b>字段长度约束：返回值长度不得超过 32 字符</b>
     * （对应 skill_log.skill_name DDL {@code VARCHAR(32)}）。</p>
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

    /**
     * 获取使用此技能所需的权限节点
     *
     * <p>用于意图识别阶段的权限预检过滤：
     * 如果调用者没有此权限，Skill 描述不会注入 LLM 提示词。</p>
     *
     * <p>所有 Skill 必须声明权限节点（强制）。服主通过权限插件灵活分配。</p>
     *
     * <p>内置 Skill 由 Kilacraft-AI 统一管理权限节点（在 plugin.yml 中声明）；
     * 第三方 Skill 由其自身插件注册权限节点（在自身的 plugin.yml 中声明），
     * 本插件仅通过 {@link Player#hasPermission(String)} 实时查询，不关心权限来源。</p>
     *
     * <p>权限节点的 default 值控制了默认行为：
     * {@code default: true} 表示所有玩家可用，{@code default: op} 表示仅管理员可用。</p>
     *
     * @return 权限节点（不允许返回 null）
     */
    String getRequiredPermission();
}
