package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.i18n.I18nService;

import java.util.regex.Pattern;

/**
 * SkillResult 输出归一化层（框架内部，不进 SPI jar）。
 *
 * <p>在输出给 LLM 的边界把 SkillResult 统一渲染为 {@code "[STATUS] 正文"}，
 * 状态由 {@link SkillResult#getStatus()} 直接得出，<b>无文本嗅探</b>。
 * 同时剥离 message 中意外残留的 bracket 前缀以防双标。</p>
 *
 * <p>单意图失败注入（{@code AIRequestHandler}）与多步骤汇总（{@code AnalysisSummary}）
 * 共用本类，确保对 LLM 的 marker 输出全局一致。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-16
 */
public final class SkillResultFormatter {

    /**
     * 匹配开头的 [STATUS] marker（ASCII 大写/下划线），用于剥离意外残留的前缀防双标。
     */
    private static final Pattern LEADING_BRACKET = Pattern.compile("^\\[[A-Z_]+\\]\\s*");

    private SkillResultFormatter() {
    }

    /**
     * 创建框架内部的 SKIPPED 结果（多步骤依赖未满足 / 参数解析失败）。
     *
     * <p>本类不在 SPI jar 中，第三方无法经编译期 API 产生 SKIPPED 状态。
     * 仅供框架内部（如 {@code TaskExecutor}）调用。</p>
     *
     * @param message 跳过原因（裸文本）
     * @return SKIPPED 结果
     */
    public static SkillResult skipped(String message) {
        return new SkillResult(false, SkillStatus.SKIPPED, I18nService.tr(message), null);
    }

    /**
     * 将 SkillResult 渲染为 {@code "[STATUS] 正文"}。
     *
     * @param result 技能结果
     * @return 形如 "[FAILURE] 余额不足" 的统一 marker 文本
     */
    public static String toLlmText(SkillResult result) {
        return toLlmText(result.getStatus().name(), result.getMessage());
    }

    /**
     * 按状态名与消息渲染为 {@code "[STATUS] 正文"}（供 {@code AnalysisSummary} 等持有状态字符串的场景）。
     *
     * @param statusName 状态名（SUCCESS/FAILURE/NEED_INFO/SKIPPED 等）
     * @param message    裸消息（可能含意外残留的前缀，会被剥离）
     * @return 形如 "[STATUS] 正文" 的统一 marker 文本
     */
    public static String toLlmText(String statusName, String message) {
        String body = (message == null || message.isEmpty()) ? "" : LEADING_BRACKET.matcher(message).replaceFirst("");
        return "[" + statusName + "] " + body;
    }
}
