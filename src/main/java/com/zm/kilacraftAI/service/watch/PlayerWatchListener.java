package com.zm.kilacraftAI.service.watch;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 全局单例事件监听器：监听 11 种高价值 Bukkit 事件，命中 filter 后回调 {@link WatchService} 触发通知。
 *
 * <p><b>单例架构</b>：由 WatchService 初始化时创建并注册一次（{@code Bukkit.getPluginManager().registerEvents}），
 * 常驻整个插件生命周期。事件命中后通过 {@link WatchService#findEventWatches} 反向索引定位订阅者</p>
 *
 * <p><b>归属模式</b>
 * <ul>
 *   <li>玩家自身事件（死亡/传送/升级/换世界/破坏方块/钓鱼/聊天）：event.getPlayer() == 订阅者</li>
 *   <li>击杀者归属（实体死亡）：event.getEntity().getKiller() == 订阅者</li>
 *   <li>坐标距离归属（熔炉/作物/实体生成）：事件位置距 watch 创建时快照位置 ≤ 半径</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public final class PlayerWatchListener implements Listener {

    private static final String LOG_MODULE = "自定义监听";
    private final WatchService service;

    PlayerWatchListener(WatchService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<WatchService.Watch> watches = service.findEventWatches("player_death", event.getEntity().getUniqueId());
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "player_death", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        List<WatchService.Watch> watches = service.findEventWatches("player_teleport", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        String cause = event.getCause() != null ? event.getCause().name() : null;
        dispatchEvent(watches, "player_teleport", "cause", cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        List<WatchService.Watch> watches = service.findEventWatches("player_level_change", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "player_level_change", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        List<WatchService.Watch> watches = service.findEventWatches("player_changed_world", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "player_changed_world", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        List<WatchService.Watch> watches = service.findEventWatches("block_break", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        String blockType = event.getBlock().getType().name();
        dispatchEvent(watches, "block_break", "block_type", blockType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // 仅通知钓到东西，不通知抛竿/收竿
        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.CAUGHT_FISH && state != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        List<WatchService.Watch> watches = service.findEventWatches("player_fish", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "player_fish", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        // 聊天事件在异步线程触发；关键词 filter 在 dispatchEvent 内按 watch 级别判定
        List<WatchService.Watch> watches = service.findEventWatches("player_chat", event.getPlayer().getUniqueId());
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "player_chat", "keyword", event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        List<WatchService.Watch> watches = service.findEventWatches("entity_death", killer.getUniqueId());
        if (watches.isEmpty()) return;
        EntityType type = event.getEntityType();
        dispatchEvent(watches, "entity_death", "entity_type", type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // 末次烧炼判定：源物品仅剩 1 个时才触发，避免一组物品每烧一个都通知
        ItemStack source = event.getSource();
        if (source.getAmount() > 1) return;
        List<WatchService.Watch> watches = service.findEventWatches("furnace_smelt", event.getBlock().getLocation(), WatchConstants.FURNACE_RADIUS);
        if (watches.isEmpty()) return;
        ItemStack result = event.getResult();
        String resultType = result.getType().name();
        dispatchEvent(watches, "furnace_smelt", "result_type", resultType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        // 成熟度判定：必须用 getNewState()（getBlock().getBlockData() 返回的是生长前旧数据）
        BlockState newState = event.getNewState();
        BlockData newData = newState.getBlockData();
        if (newData instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            // 未达最大年龄，非成熟生长
            return;
        }
        List<WatchService.Watch> watches = service.findEventWatches("crop_mature", event.getBlock().getLocation(), WatchConstants.CROP_RADIUS);
        if (watches.isEmpty()) return;
        String cropType = newState.getType().name();
        dispatchEvent(watches, "crop_mature", "crop_type", cropType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        // 玩家生成不监听
        if (entity instanceof Player) return;
        List<WatchService.Watch> watches = service.findEventWatches("entity_spawn", event.getLocation(), WatchConstants.ENTITY_RADIUS);
        if (watches.isEmpty()) return;
        dispatchEvent(watches, "entity_spawn", "entity_type", entity.getType().name());
    }

    /**
     * 分发事件到已命中的 watch 集合（filter 级别二次筛选）。
     *
     * @param watches     索引查询命中的 watch 列表（已通过归属判定）
     * @param eventType   事件类型名（与 Watch.eventType 对应）
     * @param filterKey   filter 参数键（如 "block_type"），null 表示无类型 filter
     * @param filterValue 事件提供的 filter 值（如 "DIAMOND_ORE"）
     */
    private void dispatchEvent(List<WatchService.Watch> watches, String eventType, String filterKey, String filterValue) {
        for (WatchService.Watch watch : watches) {
            if (matchesFilter(watch, filterKey, filterValue)) {
                service.triggerEvent(watch, eventType, filterValue);
            } else {
                // 有订阅者且归属匹配，但 filter 不命中
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("事件监听 filter 不匹配（监听 {}，期望 {}={}，实际={}）", watch.watchId(), filterKey, watch.filterParams() != null ? watch.filterParams().get(filterKey) : "?", filterValue));
            }
        }
    }

    /**
     * 判定 watch 的 filter 是否与事件提供的值匹配。
     *
     * <p>filter 匹配规则：
     * <ul>
     *   <li>filterKey 为 null（事件无类型化 filter）：watch 无该 filter 键或值为空则匹配</li>
     *   <li>filterKey 为 "keyword"（聊天）：watch 的 keyword 包含在消息中即匹配（大小写不敏感）</li>
     *   <li>其他 filterKey：watch 的 filter 值与事件值忽略大小写相等即匹配</li>
     * </ul>
     */
    private static boolean matchesFilter(WatchService.Watch watch, String filterKey, String filterValue) {
        if (filterKey == null) {
            // 事件无类型化 filter，任意 watch 都匹配（具体 filter 由 watch 自身决定）
            return true;
        }
        Map<String, String> filters = watch.filterParams();
        if (filters == null || filters.isEmpty()) {
            // watch 无 filter，任意事件匹配
            return true;
        }
        String watchFilterValue = filters.get(filterKey);
        if (watchFilterValue == null || watchFilterValue.isBlank()) {
            // watch 未设置此 filter
            return true;
        }
        if ("keyword".equals(filterKey)) {
            // 聊天关键词：包含匹配（大小写不敏感）
            return filterValue != null && filterValue.toLowerCase().contains(watchFilterValue.toLowerCase());
        }
        // 其他 filter：忽略大小写相等
        return watchFilterValue.equalsIgnoreCase(filterValue);
    }
}
