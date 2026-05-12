package com.zm.kilacraftAI.event;

import lombok.Getter;

/**
 * 服务器事件类型枚举
 *
 * <p>定义所有可采集的服务器事件类型，用于事件表 {@code kca_server_event} 的 {@code event_type} 字段。</p>
 *
 * <p><b>字段长度约束：枚举名 name().length() 不得超过 32 字符</b>（对应 DDL {@code VARCHAR(32)}）。
 * 新增枚举项时必须检查长度，避免写入截断或报错。</p>
 *
 * @author Zm_Mmm
 */
@Getter
public enum ServerEventType {

    // 玩家生命周期
    PLAYER_LOGIN("玩家登录"), PLAYER_LOGOUT("玩家登出"), PLAYER_FIRST_JOIN("首次加入"),

    // 市场事件
    MARKET_ITEM_SOLD("商品售出"), MARKET_ITEM_LISTED("商品上架"), MARKET_MONEY_RECEIVED("收到钱款"),

    // 玩家行为
    PLAYER_DEATH("玩家死亡"), PLAYER_ADVANCEMENT("达成成就"), PLAYER_LEVEL_UP("玩家升级"),

    // 好友动态
    PLAYER_USE_TOTEM("触发不死图腾"), PLAYER_DEFEAT_BOSS("击杀BOSS"), PLAYER_COMPLETE_RAID("完成袭击"), PLAYER_PET_DEATH("宠物死亡"), PLAYER_PVP_KILL("PVP击杀"), PLAYER_PVP_DEATH("被玩家击杀"), PLAYER_TOOL_BREAK("工具断裂"), PLAYER_CATCH_TREASURE("钓到宝藏"), PLAYER_LIGHTNING_STRIKE("被雷劈"), PLAYER_CURE_VILLAGER("治愈僵尸村民"),

    // 稀有事件
    PLAYER_MINE_ANCIENT_DEBRIS("挖到远古残骸"), PLAYER_TAME_ANIMAL("驯服动物"), PLAYER_CRAFT_ENCH_GOLDEN_APPLE("合成附魔金苹果"), PLAYER_BUILD_WITHER("召唤凋零");

    private final String description;

    ServerEventType(String description) {
        this.description = description;
    }

}
