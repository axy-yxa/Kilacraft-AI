package com.zm.kilacraftAI.service.event;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.model.event.ServerEvent;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.raid.RaidFinishEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 服务器事件采集器
 *
 * <p>监听 Bukkit 事件，异步写入 {@code kca_server_event} 表。</p>
 *
 * @author Zm_Mmm
 */
public class EventCollector implements Listener {

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private volatile ServerEventDao eventDao;
    /**
     * 当前服务器标识（群组服区分）
     */
    private volatile String serverId;

    public EventCollector(KilacraftAI plugin, DatabaseManager databaseManager, String serverId) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.eventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.serverId = serverId != null ? serverId : "";
    }

    /**
     * 热重载配置
     *
     * @param serverId 新的 server_id 值
     */
    public void refreshConfig(String serverId) {
        this.serverId = serverId != null ? serverId : "";
        this.eventDao = new ServerEventDao(databaseManager.getTablePrefix());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_DEATH, event.getEntity().getUniqueId(), event.getDeathMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // 过滤掉配方解锁等无关进度
        String advancementKey = event.getAdvancement().getKey().toString();
        if (advancementKey.contains("recipes/")) return;

        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_ADVANCEMENT, event.getPlayer().getUniqueId(), advancementKey));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        // 仅记录升级（不记录降级）
        if (event.getNewLevel() > event.getOldLevel()) {
            submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_LEVEL_UP, event.getPlayer().getUniqueId(), event.getOldLevel() + " → " + event.getNewLevel()));
        }
    }

    private static final Set<String> BOSS_TYPE_NAMES = Set.of("ENDER_DRAGON", "WITHER", "ELDER_GUARDIAN", "WARDEN");

    private static final Set<EntityType> PET_TYPES = EnumSet.of(EntityType.WOLF, EntityType.CAT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.PARROT, EntityType.FOX);

    private static final Set<Material> TREASURE_ITEMS = EnumSet.of(Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.SADDLE, Material.LILY_PAD, Material.NAUTILUS_SHELL, Material.BOW, Material.FISHING_ROD);

    /**
     * 不死图腾触发
     *
     * <p>EntityResurrectEvent 在实体可能复活时触发。isCancelled=true 表示没有图腾，
     * 因此 ignoreCancelled=true 只监听实际触发图腾的情况。</p>
     *
     * <p>注意：getLastDamageCause() 在复活事件中可能已被清除，
     * 改用缓存最后伤害源的方式获取真实死因。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 优先从缓存的最后伤害源获取，fallback 到空串
        String cause = "";
        if (player.getLastDamageCause() != null) {
            cause = player.getLastDamageCause().getCause().name();
        }
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_USE_TOTEM, player.getUniqueId(), cause));
    }

    /**
     * 击杀 BOSS（仅记录 WITHER/ENDER_DRAGON/ELDER_GUARDIAN/WARDEN）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        EntityType type = event.getEntityType();
        if (!BOSS_TYPE_NAMES.contains(type.name())) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_DEFEAT_BOSS, killer.getUniqueId(), type.name()));
    }

    /**
     * 完成袭击
     *
     * <p>RaidFinishEvent.getWinners() 返回所有胜利者（在线玩家）。
     * 为每个玩家独立记录一条事件。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaidComplete(RaidFinishEvent event) {
        List<Player> winners = event.getWinners();
        if (winners.isEmpty()) return;
        String waveInfo = String.valueOf(event.getRaid().getTotalWaves());
        for (Player winner : winners) {
            submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_COMPLETE_RAID, winner.getUniqueId(), I18nService.tr("{} 波", waveInfo)));
        }
    }

    /**
     * 宠物死亡
     *
     * <p>仅记录被驯服的宠物（Tameable.isTamed() + owner 是 Player）。
     * 记录在宠物主人名下，而非攻击者名下。</p>
     *
     * <p>data 格式："宠物类型 (死因) [杀手]"，如 "WOLF (ENTITY_ATTACK) [Zombie]"。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Tameable tameable)) return;
        if (!tameable.isTamed()) return;
        if (!(tameable.getOwner() instanceof Player owner)) return;
        // 只记录常见宠物类型，避免模组实体泛滥
        if (!PET_TYPES.contains(entity.getType())) return;

        String cause = entity.getLastDamageCause() != null ? entity.getLastDamageCause().getCause().name() : "UNKNOWN";
        StringBuilder data = new StringBuilder(entity.getType().name()).append(" (").append(cause).append(")");

        // 追加杀手实体信息（如被僵尸打死、被骷髅射死等）
        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            // 如果杀手是投射物（如箭），追溯到真正的发射者
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            data.append(" [").append(damager.getType().name()).append("]");
        }

        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_PET_DEATH, owner.getUniqueId(), data.toString()));
    }

    /**
     * PVP 击杀（双向记录）
     *
     * <p>PlayerDeathEvent.getEntity().getKiller() 返回造成最后一击的玩家。
     * 同时记录杀手侧（PLAYER_PVP_KILL）和受害者侧（PLAYER_PVP_DEATH）。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        // 排除自杀（虽然理论上不会发生，防御性判断）
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;
        // 杀手侧：击杀了谁
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_PVP_KILL, killer.getUniqueId(), victim.getUniqueId(), victim.getName()));
        // 受害者侧：被谁击杀
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_PVP_DEATH, victim.getUniqueId(), killer.getUniqueId(), killer.getName()));
    }

    /**
     * 工具/装备耐久耗尽
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToolBreak(PlayerItemBreakEvent event) {
        Material type = event.getBrokenItem().getType();
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_TOOL_BREAK, event.getPlayer().getUniqueId(), type.name()));
    }

    /**
     * 钓到宝藏（仅记录附魔书、命名牌、鞍等稀有物品）
     *
     * <p>PlayerFishEvent.getState() == CAUGHT_FISH 且 getCaught() instanceof Item
     * 时，检查物品类型是否为宝藏级。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishTreasure(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item item)) return;
        Material type = item.getItemStack().getType();
        if (!TREASURE_ITEMS.contains(type)) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_CATCH_TREASURE, event.getPlayer().getUniqueId(), type.name()));
    }

    /**
     * 被雷劈
     *
     * <p>EntityDamageEvent 中 cause == LIGHTNING 且实体是 Player。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrike(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_LIGHTNING_STRIKE, player.getUniqueId(), ""));
    }

    /**
     * 治愈僵尸村民
     *
     * <p>EntityTransformEvent.TransformReason == CURED 时，检查原实体周围是否有玩家。
     * 1.16.5 API 中治愈事件没有直接关联玩家，使用距离最近玩家作为归属者。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCureVillager(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) return;
        if (!(event.getEntity() instanceof ZombieVillager)) return;
        // 僵尸村民转换位置附近最近的在线玩家
        Player nearest = getNearestPlayer(event.getEntity(), 10.0);
        if (nearest == null) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_CURE_VILLAGER, nearest.getUniqueId(), ""));
    }

    /**
     * 挖到远古残骸
     *
     * <p>BlockBreakEvent 中被破坏方块为 ANCIENT_DEBRIS 时触发。
     * 远古残骸是下界最稀有矿物，仅在 y=8-22 层生成，单区块最多 5 个。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineAncientDebris(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.ANCIENT_DEBRIS) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_MINE_ANCIENT_DEBRIS, event.getPlayer().getUniqueId(), ""));
    }

    /**
     * 驯服动物
     *
     * <p>EntityTameEvent 在动物被成功驯服时触发（非每次交互，仅最终成功时）。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTameAnimal(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        String entityType = event.getEntityType().name();
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_TAME_ANIMAL, player.getUniqueId(), entityType));
    }

    /**
     * 合成附魔金苹果
     *
     * <p>CraftItemEvent 中结果物品为 ENCHANTED_GOLDEN_APPLE 时触发。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftEnchantedGoldenApple(CraftItemEvent event) {
        var recipe = event.getRecipe();
        if (recipe.getResult().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_CRAFT_ENCH_GOLDEN_APPLE, player.getUniqueId(), ""));
    }

    /**
     * 召唤凋零
     *
     * <p>CreatureSpawnEvent 中实体类型为 WITHER 且 SpawnReason 为 BUILD_WITHER 时触发。
     * 凋零生成事件本身没有玩家引用，通过距离最近玩家归属（同治愈僵尸村民策略）。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuildWither(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.WITHER) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_WITHER) return;
        Player nearest = getNearestPlayer(event.getEntity(), 10.0);
        if (nearest == null) return;
        submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_BUILD_WITHER, nearest.getUniqueId(), ""));
    }

    /**
     * 获取距离实体最近的在线玩家
     *
     * @param entity 参考实体
     * @param range  最大搜索距离（格）
     * @return 最近的玩家，超出范围返回 null
     */
    private Player getNearestPlayer(Entity entity, double range) {
        List<Player> nearby = entity.getNearbyEntities(range, range, range).stream().filter(e -> e instanceof Player).map(e -> (Player) e).filter(Player::isOnline).toList();
        if (nearby.isEmpty()) return null;
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : nearby) {
            double dist = p.getLocation().distanceSquared(entity.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * 异步写入事件到 DB（公共 API，供其他组件调用）
     */
    public void submitEvent(ServerEvent event) {
        final String currentServerId = this.serverId;
        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                eventDao.insert(conn, event, currentServerId);
            } catch (Exception e) {
                PluginLoggerUtil.error("数据库", "写入服务器事件失败: {}", e.getMessage());
            }
        });
    }
}
