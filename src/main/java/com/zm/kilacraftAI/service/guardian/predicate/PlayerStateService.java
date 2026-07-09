package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玩家结构化状态读取服务：在主线程/玩家所在区域线程一次性采集 Bukkit 状态，返回不可变 {@link PlayerState} 快照。
 *
 * <p>Folia 安全：Spigot 主线程直跑；否则经 {@link FoliaCompat#callSyncOnEntity} 切玩家区域线程。
 * 整条采集在一次同步回合内完成，避免多次跨线程往返。谓词在 IO 线程只读快照，永不裸调 Bukkit API。</p>
 *
 * <p>拉取式采集：仅当「守护已启用 + 玩家在线 + 某轮询 monitor 到点」时才取数；
 * 单 tick 工作量 = (启用守护的在线玩家) × (到点的轮询 monitor)，不随全员膨胀。</p>
 *
 * <p>与 {@link com.zm.kilacraftAI.service.player.PlayerMetaCollector} 并行：后者产出文本喂 LLM prompt，
 * 本服务产出结构化数据喂谓词求值，职责分离。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class PlayerStateService {

    private static final String LOG_MODULE = "守护系统";
    private static final long SYNC_TIMEOUT_SECONDS = 5L;
    private static final int DEFAULT_MAX_NEARBY = 200;

    /** 附近实体扫描半径（格）。守护关心"附近威胁"，16 格覆盖多数感知场景，避免密集区域卡 tick。 */
    private volatile double scanRadius = 16.0;
    /** 单次快照最多保留的附近实体条数，防御刷怪塔等极端场景。 */
    private volatile int maxNearbyEntities = DEFAULT_MAX_NEARBY;

    /** 默认快照：只采玩家自身状态，不读额外方块。 */
    public PlayerState snapshot(Player player) {
        return snapshot(player, Set.of());
    }

    /**
     * 带方块请求的快照：额外读取指定位置的熔炉（供 FurnaceCookCompletePredicate 等）。
     *
     * @param furnacePositions 需要读取的熔炉位置集合；空集等同 {@link #snapshot(Player)}
     * @return 玩家在线时返回快照；玩家为 null/离线返回 null
     */
    public PlayerState snapshot(Player player, Set<BlockPos> furnacePositions) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Set<BlockPos> positions = furnacePositions == null ? Set.of() : furnacePositions;
        // Spigot 主线程：直接读，避免 callSyncMethod 排队自死锁；Folia/异步：切玩家区域线程
        if (!FoliaCompat.isFolia() && Bukkit.isPrimaryThread()) {
            return doSnapshot(player, positions);
        }
        return FoliaCompat.callSyncOnEntity(player, () -> doSnapshot(player, positions), SYNC_TIMEOUT_SECONDS);
    }

    public void setScanRadius(double scanRadius) {
        this.scanRadius = scanRadius;
    }

    public void setMaxNearbyEntities(int maxNearbyEntities) {
        this.maxNearbyEntities = maxNearbyEntities;
    }

    /**
     * 实际采集逻辑：直接调 Bukkit API，必须在主线程/玩家区域线程执行。
     * 包私有：避免触发 Folia 调度，可直接验证采集逻辑。
     */
    PlayerState doSnapshot(Player player, Set<BlockPos> furnacePositions) {
        // 生命
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        double healthRatio = maxHealth > 0 ? Math.max(0.0, Math.min(1.0, health / maxHealth)) : 0.0;

        // 物品聚合
        Map<Material, Integer> counts = collectInventoryCounts(player);

        // 环境
        World world = player.getWorld();
        long worldTime = world.getTime();
        WeatherCondition weather = mapWeather(world);

        // 附近实体
        Location origin = player.getLocation();
        List<PlayerState.NearbyEntity> nearby = collectNearbyEntities(world, origin);

        // 方块态（仅按请求位置读取）
        Map<BlockPos, PlayerState.FurnaceRead> furnaceReads = collectFurnaces(furnacePositions);

        return new PlayerState(
                player.getName(),
                world.getName(),
                world.getEnvironment(),
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                origin.getYaw(),
                health, maxHealth, healthRatio,
                player.getFoodLevel(), player.getLevel(),
                player.getRemainingAir(), player.getMaximumAir(),
                counts,
                worldTime, weather, nearby,
                player.getFireTicks() > 0,
                player.isInWater(),
                player.isFlying(),
                player.isGliding(),
                player.isSwimming(),
                furnaceReads
        );
    }

    private static Map<Material, Integer> collectInventoryCounts(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] items = inv.getContents();
        Map<Material, Integer> counts = new HashMap<>();
        if (items == null) {
            return counts;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return counts;
    }

    private List<PlayerState.NearbyEntity> collectNearbyEntities(World world, Location origin) {
        Collection<Entity> raw;
        try {
            raw = world.getNearbyEntities(origin, scanRadius, scanRadius, scanRadius);
        } catch (Exception e) {
            // 区域边界/卸载等异常不应中断快照采集
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("采集附近实体失败: {}", e.getMessage()));
            return List.of();
        }
        // 玩家水平朝向单位向量（Bukkit yaw：0=南 +Z，90=西 -X）
        double yawRad = Math.toRadians(origin.getYaw());
        double facingX = -Math.sin(yawRad);
        double facingZ = Math.cos(yawRad);
        double originX = origin.getX();
        double originZ = origin.getZ();

        List<PlayerState.NearbyEntity> out = new ArrayList<>(Math.min(raw.size(), maxNearbyEntities));
        for (Entity e : raw) {
            // 玩家接近感知走 PlayerMoveEvent 事件源，不混入实体计数
            if (e instanceof Player) {
                continue;
            }
            Location entityLoc;
            try {
                entityLoc = e.getLocation();
            } catch (Exception ignored) {
                continue;
            }
            double dist;
            double angleDeg;
            try {
                dist = entityLoc.distance(origin);
                angleDeg = relativeAngle(entityLoc.getX(), entityLoc.getZ(), originX, originZ, facingX, facingZ);
            } catch (Exception ignored) {
                // 跨世界/未加载坐标：跳过该实体
                continue;
            }
            out.add(new PlayerState.NearbyEntity(e.getType(), dist, angleDeg));
        }
        out.sort(Comparator.comparingDouble(PlayerState.NearbyEntity::distance));
        if (out.size() > maxNearbyEntities) {
            out = new ArrayList<>(out.subList(0, maxNearbyEntities));
        }
        return out;
    }

    /**
     * 实体相对玩家朝向的角度（0°=正前、180°=正后）。用水平方向点积反 cos，
     * 玩家与实体近重合时按 0°（正前）处理。
     */
    private static double relativeAngle(double entityX, double entityZ, double originX, double originZ,
                                        double facingX, double facingZ) {
        double dx = entityX - originX;
        double dz = entityZ - originZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1e-6) {
            return 0.0;
        }
        double dot = (facingX * dx + facingZ * dz) / horiz;
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(clamped));
    }

    private static Map<BlockPos, PlayerState.FurnaceRead> collectFurnaces(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, PlayerState.FurnaceRead> out = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            World w = Bukkit.getWorld(pos.worldName());
            if (w == null) {
                continue;
            }
            Block block;
            try {
                block = w.getBlockAt(pos.x(), pos.y(), pos.z());
            } catch (Exception ignored) {
                continue;
            }
            if (!(block.getState() instanceof Furnace furnace)) {
                continue;
            }
            FurnaceInventory fi = furnace.getInventory();
            int cookTime = furnace.getCookTime();
            int cookTotal = furnace.getCookTimeTotal();
            double progress = cookTotal > 0 ? Math.min(1.0, cookTime / (double) cookTotal) : 0.0;
            out.put(pos, new PlayerState.FurnaceRead(
                    fi.getResult() != null,
                    progress,
                    fi.getSmelting() != null
            ));
        }
        return out;
    }

    private static WeatherCondition mapWeather(World world) {
        if (world.isThundering()) {
            return WeatherCondition.THUNDER;
        }
        if (world.hasStorm()) {
            return WeatherCondition.RAIN;
        }
        return WeatherCondition.CLEAR;
    }
}
