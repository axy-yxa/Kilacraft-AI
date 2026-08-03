package com.zm.kilacraftAI.service.command;

import java.util.List;

/**
 * 命令文档中的单条命令条目
 *
 * <p>由 {@link CommandDocumentParser} 从 {@code commands/commands.md} 解析产出。
 * {@code permission} 为 null 表示无权限限制（所有玩家可见）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public record CommandEntry(String command, String description, String example, String permission,
                           List<String> keywords) {
}
