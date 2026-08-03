package com.zm.kilacraftAI.common.enums;

import lombok.Getter;

/**
 * 大模型调用类型分类，按 prompt 结构聚合同类大模型请求，用于统计缓存命中率。
 * 每种类型的 prompt 模板前缀不同，缓存行为独立。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
@Getter
public enum CacheCallTypeEnum {

    /**
     * 意图识别 Phase 1 — 技能分类。system 纯静态（角色定义+输出格式+权限过滤技能列表），
     * 跨同权限玩家可共享前缀；动态上下文与玩家消息在 user 端
     */
    INTENT_PHASE1("意图识别-Phase1"),

    /**
     * 意图识别 Phase 2 — 选 action + 提取参数。system 纯静态（完整规则段 + 技能列表置于末尾），
     * 跨同权限玩家可共享前缀；知识库、动态上下文与玩家消息在 user 端
     */
    INTENT_PHASE2("意图识别-Phase2"),

    /**
     * 续体分类 — 确认/取消/回答/无效。分类契约是 system 静态主体（含一笔每请求不同的待处理操作，
     * 故可缓存前缀仅契约模板部分）；动态上下文与玩家本轮回复在 user 端
     */
    PENDING_RESUME("续体分类"),

    /**
     * 二次分析 — 大模型分析技能执行结果。system 纯静态（agent 提示词 + 语言约束），
     * 可全量缓存；技能结果、动态上下文与历史在 user 端（历史每轮增长损耗 system 之后的缓存）
     */
    SECONDARY_ANALYSIS("二次分析"),

    /**
     * 普通 AI 对话 — 回退或第三方插件调用。system 纯静态（人格 + 语言约束），可全量缓存；
     * 动态上下文、知识库、历史与玩家消息在 user 端（历史每轮增长损耗 system 之后的缓存）
     */
    NORMAL_CHAT("普通对话"),

    /**
     * 登录问候。问候模板是 system 主体（含大量每请求不同的离线事件/好友动态等业务占位，
     * 无法静态化）；动态上下文（含画像）在 user 端。可缓存前缀仅模板骨架
     */
    GREETING("登录问候"),

    /**
     * 玩家画像分析。player=null（后台建画像任务），system 纯静态（分析模板）可全量缓存；
     * 老画像 JSON 与对话记录在 user 端
     */
    PROFILE("玩家画像"),

    /**
     * 对话推荐追问生成。system 纯静态（推荐模板 + 权限过滤技能摘要），跨同权限玩家可共享前缀；
     * 动态上下文、历史与推荐指令在 user 端
     */
    SUGGESTION("对话推荐"),

    /**
     * 实用工具（通知美化/广播美化）。system 纯静态（美化模板），可全量缓存；
     * 动态上下文（含画像）与待美化消息在 user 端
     */
    UTILITY("实用工具"),

    /**
     * 服务器性能诊断，推理模型（thinking-model 路径）。system 纯静态（诊断模板），
     * 各项实时性能指标在 user 端
     */
    SERVER_DIAGNOSTICS("服务器诊断");

    private final String displayName;

    CacheCallTypeEnum(String displayName) {
        this.displayName = displayName;
    }

}
