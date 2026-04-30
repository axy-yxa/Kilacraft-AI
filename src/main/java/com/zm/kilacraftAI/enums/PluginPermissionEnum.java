package com.zm.kilacraftAI.enums;

import lombok.Getter;

/**
 * 权限枚举类
 * 统一管理所有权限节点
 *
 * @author Zm_Mmm
 * @since 2026-03-29
 */
@Getter
public enum PluginPermissionEnum {

    /**
     * 清除自己的对话历史
     */
    CLEAR_SELF("kilacraft.clear.self"),

    /**
     * 清除其他玩家的对话历史
     */
    CLEAR_OTHER("kilacraft.clear.other"),

    /**
     * 重载配置
     */
    RELOAD("kilacraft.reload"),

    /**
     * 管理知识库
     */
    KNOWLEDGE("kilacraft.knowledge"),

    /**
     * 管理人格配置
     */
    PERSONALITIES("kilacraft.personalities"),

    /**
     * 查询和取消挂机任务
     */
    AFK("kilacraft.afk"),

    /**
     * AI 命令执行（以玩家身份执行服务器命令）
     */
    COMMAND_EXECUTE("kilacraft.command.execute"),

    /**
     * CMI 查询功能（家、地标、玩家信息、物品价值等）
     */
    CMI_QUERY("kilacraft.cmi.query"),

    /**
     * CMI 传送功能（回家、地标传送、传送请求等）
     */
    CMI_TELEPORT("kilacraft.cmi.teleport"),

    /**
     * Bukkit 音效与粒子效果（播放音效/显示粒子）
     */
    BUKKIT_FX("kilacraft.bukkit_fx"),

    /**
     * Bukkit 原版统计数据查询（生涯累计统计）
     */
    BUKKIT_STATS("kilacraft.bukkit_stats"),

    /**
     * 通用工具技能 - 延迟等待（所有玩家可用）
     */
    UTILITY_DELAY_WAIT("kilacraft.utility.delay_wait"),

    /**
     * 通用工具技能 - 主动中途通知（所有玩家可用）
     */
    UTILITY_NOTIFY_PLAYER("kilacraft.utility.notify_player"),

    /**
     * 通用工具技能 - AI 全服广播消息（OP 管理员专用，通过 CHAT 载体向全服玩家广播 AI 美化后的消息）
     */
    UTILITY_BROADCAST("kilacraft.utility.broadcast");

    /**
     * 权限节点
     */
    private final String node;

    PluginPermissionEnum(String node) {
        this.node = node;
    }

    /**
     * 检查命令发送者是否拥有此权限
     *
     * @param sender 命令发送者
     * @return 是否拥有权限
     */
    public boolean hasPermission(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission(node);
    }
}
