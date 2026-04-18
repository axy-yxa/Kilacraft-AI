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

    /**
     * dataMap 中主结果字段的语义化名称（可选）
     *
     * <p>对于 method_chain 模式返回简单标量（Number/Boolean/String/enum）的 API，
     * 默认 dataMap 只有 raw_result 字段，LLM 难以引用。设置此属性后，
     * 执行器会自动将主结果值以该字段名注入 dataMap。</p>
     *
     * <p>例：get_player_ping 设置 data_field="ping" 后，
     * dataMap 中既有 raw_result=42，也有 ping=42，
     * LLM 可用 {step_x.ping} 引用。</p>
     *
     * <p>additional_methods 模式无需此属性（框架已自动提取所有 key 到 dataMap）。</p>
     */
    private String dataField;
}
