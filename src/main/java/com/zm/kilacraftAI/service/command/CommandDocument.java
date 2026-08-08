package com.zm.kilacraftAI.service.command;

import java.util.List;

/**
 * 命令文档解析结果
 *
 * <p>仅存原始解析结果（全部条目），不预计算注入文本——Phase1 摘要与 Phase2 完整列表
 * 依赖当前 player 的权限过滤，无法在解析阶段预计算，由 {@code CommandSkill} 运行时拼接。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public record CommandDocument(boolean isEmpty, List<CommandEntry> entries) {
}
