package com.zm.kilacraftAI.common.enums;

import lombok.Getter;
import org.bukkit.command.CommandSender;

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
    UTILITY_BROADCAST("kilacraft.utility.broadcast"),

    /**
     * 通用工具技能（Skill 级权限，用于意图识别提示词预检）
     */
    UTILITY("kilacraft.utility"),

    /**
     * GlobalMarketPlus 市场查询技能
     */
    MARKET_QUERY("kilacraft.market.query"),

    /**
     * GlobalMarketPlus 市场操作技能
     */
    MARKET_ACTION("kilacraft.market.action"),

    /**
     * Bukkit 通用 API 技能（元数据驱动）
     */
    BUKKIT_API("kilacraft.api.*"),

    /**
     * Bukkit API 玩家物品栏查询（BukkitPlayerInventorySkill）
     */
    API_PLAYER_INVENTORY("kilacraft.api.player.inventory"),

    /**
     * Bukkit API 玩家状态查询（BukkitPlayerStatusSkill）
     */
    API_PLAYER_STATUS("kilacraft.api.player.status"),

    /**
     * Bukkit API 玩家基础信息查询（BukkitPlayerInfoSkill）
     */
    API_PLAYER_INFO("kilacraft.api.player.info"),

    /**
     * Bukkit API 世界信息查询（BukkitWorldSkill）
     */
    API_WORLD_INFO("kilacraft.api.world.info"),

    /**
     * Bukkit API 服务器信息查询（BukkitServerSkill）
     */
    API_SERVER_INFO("kilacraft.api.server.info"),

    /**
     * 查看定时任务运行状态
     */
    TASKS("kilacraft.tasks"),

    /**
     * 守护系统（启用/停用守护、查看状态）
     */
    GUARDIAN("kilacraft.guardian"),

    /**
     * 跨玩家上下线订阅（PlayerWatchSkill）
     */
    PLAYER_WATCH("kilacraft.player_watch"),

    /**
     * 玩家自定义监听（WatchSkill）
     */
    WATCH("kilacraft.watch"),

    /**
     * 服务器健康管理（手动采样命令 + 告警/报告通知接收 + 历史告警查询 + 外部通知渠道测试）
     */
    ADMIN_HEALTH("kilacraft.admin.health"),

    /**
     * PlayerAnalysisSkill（在线趋势、活跃排行、新玩家流入、画像覆盖率、社交图谱）
     */
    ADMIN_PLAYER("kilacraft.admin.player"),

    /**
     * AuditLogSkill（技能执行记录查询、使用统计、失败日志）
     */
    ADMIN_AUDIT("kilacraft.admin.audit"),

    /**
     * 查询类命令自查看统一节点：查看自己的 AI 数据（用量 / 对话历史 / 画像）
     */
    QUERY_SELF("kilacraft.query.self"),

    /**
     * 查看任意玩家的 AI 用量统计 + 全服概览
     */
    USAGE_OTHER("kilacraft.usage.other"),

    /**
     * 查看任意玩家的对话历史
     */
    HISTORY_OTHER("kilacraft.history.other"),

    /**
     * 查看任意玩家的玩家画像 / AI 记忆
     */
    MEMORY_OTHER("kilacraft.memory.other"),

    /**
     * 插件运行信息与配置自检（doctor 自检 + about 版本检查），与性能健康 admin.health 区分
     */
    ADMIN_INFO("kilacraft.admin.info"),

    /**
     * 联网搜索技能（WebSearchSkill）
     */
    WEB_SEARCH("kilacraft.websearch"),

    /**
     * 网页抓取技能（WebFetchSkill）
     */
    WEB_FETCH("kilacraft.webfetch"),

    /**
     * 大模型缓存命中率统计查看
     */
    ADMIN_CACHE("kilacraft.admin.cache");

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
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(node);
    }
}
