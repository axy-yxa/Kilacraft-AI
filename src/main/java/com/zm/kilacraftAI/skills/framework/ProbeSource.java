package com.zm.kilacraftAI.skills.framework;

import java.util.Set;

/**
 * 可选能力接口：声明本 skill 哪些 action 可被 Watch 系统监听（轮询型条件监听）。
 *
 * <p>这是 {@link Skill} 之上的<b>可选增强</b>，只有提供只读查询 action 且适合被周期性轮询的 skill 实现它。
 * Watch 系统按 {@code instanceof ProbeSource} 判断，未实现的 skill 不受影响。</p>
 *
 * <p><b>安全约束</b>：只应返回纯只读、无副作用的查询 action（如查血量/查余额/查统计）。
 * 绝不应返回有副作用的 action（如转账/传送/执行命令/上架），否则会被反复轮询执行造成严重问题。
 * 内置 skill 由项目方审核保证安全性。</p>
 *
 * <p><b>语义边界</b>：probe 执行时 player 恒为 owner（订阅者），查询的是 owner 自身状态
 * 或 owner 所在世界的状态——这是 SkillSecurityFilter 数据隔离的必然要求（player==null 会
 * 跳过消毒打开隔离缺口）。故 ProbeSource 不适用于查全服/管理级数据的 admin skill：
 * 即使它们有只读查询 action 且有 ADMIN 权限保护，其查询语义（全服趋势/审计日志/服务器健康）
 * 与"玩家盯自己关心的值"的个人状态边界不符。</p>
 *
 * <p><b>不进 SPI jar</b>：这是内部接口（不在 {@code src/assembly/skill-api.xml} 白名单内），
 * 第三方 skill 无法实现它。第三方若想让其查询能力被监听，需提交审核加入内置 skill。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public interface ProbeSource {

    /**
     * 返回本 skill 可被 Watch 系统监听的 action 名集合（只读查询类）。
     *
     * <p>返回的 action 名必须与 {@link Skill#getActions()} 的 key 一致。
     * Watch 系统会在创建轮询型监听时校验请求的 action 是否在此集合内。</p>
     *
     * @return 可监听 action 名的不可变集合，空集合表示本 skill 无可监听 action
     */
    Set<String> getProbeableActions();
}
