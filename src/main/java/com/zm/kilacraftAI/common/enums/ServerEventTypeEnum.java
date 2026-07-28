package com.zm.kilacraftAI.common.enums;

import lombok.Getter;

/**
 * 服务器事件类型枚举
 *
 * <p>定义所有可采集的服务器事件类型，用于事件表 {@code kca_server_event} 的 {@code event_type} 字段。</p>
 *
 * <p><b>字段长度约束：枚举名 name().length() 不得超过 32 字符</b>（对应 DDL {@code VARCHAR(32)}）。
 * 新增枚举项时必须检查长度，避免写入截断或报错。</p>
 *
 * <p>每个事件类型带有 {@link Category} 分类标记，用于 SQL 查询的动态过滤。
 * {@code Category != PLAYER} 的事件类型会被自动排除出面向玩家的统计查询，
 * 无需在 SQL 中硬编码排除列表。</p>
 *
 * @author Zm_Mmm
 */
@Getter
public enum ServerEventTypeEnum {

    // 玩家生命周期（不参与玩家统计）
    PLAYER_LOGIN("玩家登录", Category.LIFECYCLE), PLAYER_LOGOUT("玩家登出", Category.LIFECYCLE), PLAYER_FIRST_JOIN("首次加入", Category.LIFECYCLE),

    // 市场事件（隐私排除）
    MARKET_ITEM_SOLD("商品售出", Category.MARKET), MARKET_ITEM_LISTED("商品上架", Category.MARKET), MARKET_MONEY_RECEIVED("收到钱款", Category.MARKET),

    // 健康告警（系统级事件，不参与玩家统计）
    HEALTH_ALERT("健康告警", Category.SYSTEM),

    // 健康告警已通知标记（带 player_uuid，data 与对应 HEALTH_ALERT 一致用于精确匹配）
    HEALTH_ALERT_NOTIFIED("告警已通知", Category.SYSTEM),

    // 新版本可用提醒（系统级事件，不参与玩家统计）
    UPDATE_AVAILABLE("更新可用", Category.SYSTEM),

    // 新版本已通知标记（记录某管理员已被通知某版本，带 player_uuid，每玩家每版本一条）
    UPDATE_NOTIFIED("更新已通知", Category.SYSTEM),

    // 玩家行为（面向玩家的事件，参与统计）
    PLAYER_DEATH("玩家死亡", Category.PLAYER), PLAYER_ADVANCEMENT("达成成就", Category.PLAYER), PLAYER_LEVEL_UP("玩家升级", Category.PLAYER),

    // 好友动态
    PLAYER_USE_TOTEM("触发不死图腾", Category.PLAYER), PLAYER_DEFEAT_BOSS("击杀BOSS", Category.PLAYER), PLAYER_COMPLETE_RAID("完成袭击", Category.PLAYER), PLAYER_PET_DEATH("宠物死亡", Category.PLAYER), PLAYER_PVP_KILL("PVP击杀", Category.PLAYER), PLAYER_PVP_DEATH("被玩家击杀", Category.PLAYER), PLAYER_TOOL_BREAK("工具断裂", Category.PLAYER), PLAYER_CATCH_TREASURE("钓到宝藏", Category.PLAYER), PLAYER_LIGHTNING_STRIKE("被雷劈", Category.PLAYER), PLAYER_CURE_VILLAGER("治愈僵尸村民", Category.PLAYER),

    // 稀有事件
    PLAYER_MINE_ANCIENT_DEBRIS("挖到远古残骸", Category.PLAYER), PLAYER_TAME_ANIMAL("驯服动物", Category.PLAYER), PLAYER_CRAFT_ENCH_GOLDEN_APPLE("合成附魔金苹果", Category.PLAYER), PLAYER_BUILD_WITHER("召唤凋灵", Category.PLAYER),

    // 玩家自定义监听触发（监听条件满足时记录，供问候系统回顾）
    PLAYER_WATCH_TRIGGERED("监听触发", Category.PLAYER);

    private final String description;
    private final Category category;

    ServerEventTypeEnum(String description, Category category) {
        this.description = description;
        this.category = category;
    }

    /**
     * 事件分类标记
     *
     * <p>用于 SQL 查询的动态过滤：{@code Category != PLAYER} 的事件
     * 会被 {@code buildPlayerFacingExcludeFilter()} 自动排除，
     * 新增非玩家事件类型时无需修改任何 SQL。</p>
     */
    public enum Category {
        /**
         * 生命周期事件（登录/登出/首次加入），排除出玩家统计
         */
        LIFECYCLE,
        /**
         * 市场隐私事件，排除出玩家统计
         */
        MARKET,
        /**
         * 系统级事件（非玩家事件，如健康告警），排除出玩家统计
         */
        SYSTEM,
        /**
         * 面向玩家的行为事件，参与统计
         */
        PLAYER
    }
}
