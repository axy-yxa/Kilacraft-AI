package com.zm.kilacraftAI.service.guardian.predicate;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * 玩家状态快照：不可变、纯数据。由 {@link PlayerStateService} 在主线程/玩家区域线程一次性采集，
 * 供守护谓词在 IO 线程只读求值。谓词永不直接碰 Bukkit API。
 *
 * <p>只采集内置 monitor 实际消费的字段：背包剩余空格、是否正打开容器、附近实体、耐久最低的装备。
 * {@link #nearbyEntities} 已按距离升序，长度受 {@code PlayerStateService.maxNearbyEntities} 上限约束。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record PlayerState(String playerName, int inventoryFreeSlots, boolean inventoryOpen,
                          List<NearbyEntity> nearbyEntities, LowDurabilityItem lowestDurabilityItem) {

    public PlayerState {
        nearbyEntities = nearbyEntities == null ? List.of() : List.copyOf(nearbyEntities);
    }

    /**
     * 附近实体记录。
     *
     * @param distance         3D 直线距离（格）
     * @param relativeAngleDeg 相对玩家朝向的角度：0°=正前、90°=侧方、180°=正后。由 PlayerStateService
     *                         按玩家 yaw 与实体相对位置预算；ThreatOutOfViewAndNearPredicate 用它判定视野外威胁
     */
    public record NearbyEntity(EntityType type, double distance, double relativeAngleDeg) {
    }

    /**
     * 耐久最低的可损坏装备（手持或盔甲）。
     *
     * @param material      物品材质（供 LLM 渲染「你的镐子快碎了」）
     * @param remainingUses 剩余使用次数（maxUses - damage）
     * @param maxUses       最大耐久
     */
    public record LowDurabilityItem(Material material, int remainingUses, int maxUses) {
        /**
         * 剩余耐久百分比（0.0~1.0）。
         */
        public double remainingRatio() {
            return maxUses <= 0 ? 1.0 : (double) remainingUses / maxUses;
        }
    }
}
