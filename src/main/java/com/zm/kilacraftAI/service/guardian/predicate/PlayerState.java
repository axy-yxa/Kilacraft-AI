package com.zm.kilacraftAI.service.guardian.predicate;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 玩家状态快照：不可变、纯数据。由 {@link PlayerStateService} 在主线程/玩家区域线程一次性采集，
 * 供守护谓词在 IO 线程只读求值。谓词永不直接碰 Bukkit API。
 *
 * <p>字段按「身份/生命/物品/环境/状态标志」分组；分组只为可读，不影响语义。
 * {@link #nearbyEntities} 已按距离升序，长度受 {@code PlayerStateService.maxNearbyEntities} 上限约束。
 * {@link #furnaceReads} 仅包含本快照显式请求读取的熔炉位置（拉取式，§6.7）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record PlayerState(
        // 身份与环境
        String playerName,
        String worldName,
        World.Environment environment,
        int blockX, int blockY, int blockZ,
        float facingYaw,
        // 生命
        double health,
        double maxHealth,
        double healthRatio,
        int foodLevel,
        int xpLevel,
        int remainingAir,
        int maxAir,
        // 物品聚合
        Map<Material, Integer> inventoryCounts,
        // 环境
        long worldTime,
        WeatherCondition weather,
        List<NearbyEntity> nearbyEntities,
        // 状态标志
        boolean onFire,
        boolean inWater,
        boolean flying,
        boolean gliding,
        boolean swimming,
        // 方块态（按谓词请求的位置读取；默认空）
        Map<BlockPos, FurnaceRead> furnaceReads
) {

    public PlayerState {
        inventoryCounts = inventoryCounts == null ? Map.of() : Collections.unmodifiableMap(new java.util.HashMap<>(inventoryCounts));
        nearbyEntities = nearbyEntities == null ? List.of() : Collections.unmodifiableList(new java.util.ArrayList<>(nearbyEntities));
        furnaceReads = furnaceReads == null ? Map.of() : Collections.unmodifiableMap(new java.util.LinkedHashMap<>(furnaceReads));
    }

    /** 该物材质的背包持有数量（聚合背包所有格），0 表示未持有。 */
    public int inventoryCount(Material material) {
        return inventoryCounts.getOrDefault(material, 0);
    }

    /** 指定位置的熔炉读数，不存在返回 null。 */
    public FurnaceRead furnaceAt(BlockPos pos) {
        return furnaceReads.get(pos);
    }

    /**
     * 附近实体记录。
     *
     * @param distance          3D 直线距离（格）
     * @param relativeAngleDeg  相对玩家朝向的角度：0°=正前、90°=侧方、180°=正后。由 PlayerStateService
     *                          按玩家 yaw 与实体相对位置预算；NearbyEntityOutOfViewPredicate 用它判定视野外威胁
     */
    public record NearbyEntity(org.bukkit.entity.EntityType type, double distance, double relativeAngleDeg) {
    }

    /** 熔炉读数。{@code resultReady=true} 表示产出槽有可取出的成品。 */
    public record FurnaceRead(boolean resultReady, double cookProgress, boolean hasInput) {
    }
}
