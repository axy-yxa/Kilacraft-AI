package com.zm.kilacraftAI.greeting;

/**
 * 在线好友状态信息
 *
 * <p>包含好友名称、当前所在世界名和当前会话时长（分钟）。</p>
 * <p>世界名以原始名称输出（如 world_nether），由提示词规则约束 AI 的展示方式。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public record FriendStatus(String name, String world, long sessionMinutes) {
}
