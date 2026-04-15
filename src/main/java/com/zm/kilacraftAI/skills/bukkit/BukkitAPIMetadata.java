package com.zm.kilacraftAI.skills.bukkit;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Bukkit API 技能元数据
 *
 * <p>定义一个可被 LLM 调用的 Bukkit API 操作</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-01
 */
@Data
public class BukkitAPIMetadata {

    /**
     * API 唯一标识符（用于 LLM 识别）
     * 例："get_player_hand_item"
     */
    private String id;

    /**
     * 人类可读的名称
     * 例："获取玩家主手物品"
     */
    private String displayName;

    /**
     * 详细描述（给 LLM 看）
     * 例："获取玩家主要手持的物品信息，包括物品类型、数量、附魔等"
     */
    private String description;

    /**
     * 目标对象类型
     * 例："Player", "World", "Server"
     */
    private String targetType;

    /**
     * 方法调用链
     * 例：["getInventory", "getItemInMainHand"]
     */
    private List<String> methodChain;

    /**
     * 是否需要权限
     * 例："kilacraft.api.player.inventory"
     */
    private String requiredPermission;

    /**
     * 结果格式化模板
     * 例："你手上拿着：{item_name} x{amount}"
     */
    private String resultTemplate;

    /**
     * 额外方法映射（用于获取多个值）
     * Key: 占位符名称，Value: 方法名
     * 例：{"max_health": "getMaxHealth"}
     */
    private Map<String, String> additionalMethods;
}
