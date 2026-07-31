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
     * 意图识别 Phase 1 — 技能分类，仅玩家消息是变量
     */
    INTENT_PHASE1("意图识别-Phase1"),

    /**
     * 意图识别 Phase 2 — 选 action + 提取参数，技能详情和知识库变动
     */
    INTENT_PHASE2("意图识别-Phase2"),

    /**
     * 续体分类 — 确认/取消/回答/无效，分类模板固定
     */
    PENDING_RESUME("续体分类"),

    /**
     * 二次分析 — 大模型分析技能执行结果，分析提示词固定、结果变动
     */
    SECONDARY_ANALYSIS("二次分析"),

    /**
     * 普通 AI 对话 — 回退或第三方插件调用，人格提示词固定、历史增长
     */
    NORMAL_CHAT("普通对话"),

    /**
     * 守护系统被动发声，每次上下文完全不同
     */
    GUARDIAN("守护系统"),

    /**
     * 登录问候，问候模板固定
     */
    GREETING("登录问候"),

    /**
     * 玩家画像分析，画像 prompt 固定
     */
    PROFILE("玩家画像"),

    /**
     * 对话推荐追问生成，推荐模板固定
     */
    SUGGESTION("对话推荐"),

    /**
     * 实用工具（通知美化/广播美化），美化模板固定
     */
    UTILITY("实用工具"),

    /**
     * 服务器性能诊断，推理模型，诊断 prompt 固定
     */
    SERVER_DIAGNOSTICS("服务器诊断");

    private final String displayName;

    CacheCallTypeEnum(String displayName) {
        this.displayName = displayName;
    }

}
