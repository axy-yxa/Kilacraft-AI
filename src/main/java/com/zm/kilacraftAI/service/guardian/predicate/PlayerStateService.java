package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

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
@Setter
public final class PlayerStateService {

    private static final String LOG_MODULE = "守护系统";
    private static final long SYNC_TIMEOUT_SECONDS = 5L;
    private static final int DEFAULT_MAX_NEARBY = 200;

    /**
     * 附近实体扫描半径（格）。守护关心"附近威胁"，16 格覆盖多数感知场景，避免密集区域卡 tick。
     */
    private volatile double scanRadius = 16.0;
    /**
     * 单次快照最多保留的附近实体条数，防御刷怪塔等极端场景。
     */
    private volatile int maxNearbyEntities = DEFAULT_MAX_NEARBY;

    /**
     * 默认快照：只采玩家自身状态。
     */
    public PlayerState snapshot(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        // Spigot 主线程：直接读，避免 callSyncMethod 排队自死锁；Folia/异步：切玩家区域线程
        if (!FoliaCompat.isFolia() && Bukkit.isPrimaryThread()) {
            return doSnapshot(player);
        }
        return FoliaCompat.callSyncOnEntity(player, () -> doSnapshot(player), SYNC_TIMEOUT_SECONDS);
    }

    /**
     * 实际采集逻辑：直接调 Bukkit API，必须在主线程/玩家区域线程执行。
     * 包私有：避免触发 Folia 调度，可直接验证采集逻辑。
     */
    PlayerState doSnapshot(Player player) {
        int freeSlots = countFreeSlots(player);
        List<PlayerState.NearbyEntity> nearby = collectNearbyEntities(player, player.getLocation());
        boolean inventoryOpen = isOpenContainer(player);
        PlayerState.LowDurabilityItem lowestDurability = collectLowestDurabilityItem(player);
        return new PlayerState(player.getName(), freeSlots, inventoryOpen, nearby, lowestDurability);
    }

    private static int countFreeSlots(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] items = inv.getStorageContents();
        int free = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    /**
     * 玩家是否正打开物品 GUI（箱子/熔炉/工作台/铁砧等，含自身背包）。
     * 玩家打开自身背包（PLAYER）也在查看物品/装备状态，应视为已感知——背包快满、装备耐久告警此时多余。
     * 只排除 CRAFTING（默认 2x2 合成界面，非主动打开，不显示完整物品状态）。
     */
    private static boolean isOpenContainer(Player player) {
        try {
            InventoryView view = player.getOpenInventory();
            InventoryType type = view.getType();
            return type != InventoryType.CRAFTING;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 采集玩家耐久最低的可损坏装备（手持主/副手 + 盔甲 4 件）。
     * 只关心带 Damageable meta 且 maxDamage > 0 的物品；返回剩余耐久百分比最低的一件，无则 null。
     */
    private static PlayerState.LowDurabilityItem collectLowestDurabilityItem(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] candidates = new ItemStack[6];
        candidates[0] = inv.getItemInMainHand();
        candidates[1] = inv.getItemInOffHand();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < 4 && i < armor.length; i++) {
            candidates[2 + i] = armor[i];
        }

        PlayerState.LowDurabilityItem lowest = null;
        double lowestRatio = Double.MAX_VALUE;
        for (ItemStack item : candidates) {
            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
                continue;
            }
            if (!(item.getItemMeta() instanceof Damageable dmg)) {
                continue;
            }
            int maxUses = item.getType().getMaxDurability();
            if (maxUses <= 0) {
                continue; // 不可损坏物品（方块等）
            }
            int remainingUses = maxUses - dmg.getDamage();
            double ratio = (double) remainingUses / maxUses;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                lowest = new PlayerState.LowDurabilityItem(item.getType(), remainingUses, maxUses);
            }
        }
        return lowest;
    }

    private List<PlayerState.NearbyEntity> collectNearbyEntities(Player player, Location origin) {
        Collection<Entity> raw;
        try {
            // 用实体作用域 API 而非世界级 API——Folia 下只在玩家所属区域安全
            raw = player.getNearbyEntities(scanRadius, scanRadius, scanRadius);
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("采集附近实体失败: {}", e.getMessage()));
            return List.of();
        }
        double yawRad = Math.toRadians(origin.getYaw());
        double facingX = -Math.sin(yawRad);
        double facingZ = Math.cos(yawRad);
        double originX = origin.getX();
        double originZ = origin.getZ();

        List<PlayerState.NearbyEntity> out = new ArrayList<>(Math.min(raw.size(), maxNearbyEntities));
        for (Entity e : raw) {
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
                continue;
            }
            out.add(new PlayerState.NearbyEntity(e.getType(), dist, angleDeg));
            if (out.size() >= maxNearbyEntities) {
                break;
            }
        }
        out.sort(Comparator.comparingDouble(PlayerState.NearbyEntity::distance));
        return out;
    }

    /**
     * 实体相对玩家朝向的角度（0°=正前、180°=正后）。用水平方向点积反 cos，
     * 玩家与实体近重合时按 0°（正前）处理。
     */
    private static double relativeAngle(double entityX, double entityZ, double originX, double originZ, double facingX, double facingZ) {
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
}
