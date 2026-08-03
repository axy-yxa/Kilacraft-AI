package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 玩家物品栏与装备查询 Skill（player.inventory 域，8 个 action）
 *
 * <p>承载原 {@code GenericBukkitAPISkill} 中权限 {@code kilacraft.api.player.inventory} 下的全部 API：
 * 主手/副手物品、背包占用、背包物品摘要、末影箱、盔甲、当前界面、打开的容器内容。
 * 入参/反参/字段名/线程模型/Folia 兼容逐项沿用原实现，零行为回归。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public class BukkitPlayerInventorySkill extends AbstractBukkitQuerySkill {

    private static final String SKILL_NAME = "bukkit_player_inv";
    private static final String LOG_PREFIX = "Bukkit物品栏查询";

    private static final String ACTION_HAND_ITEM = "get_player_hand_item";
    private static final String ACTION_OFFHAND_ITEM = "get_player_offhand_item";
    private static final String ACTION_INVENTORY_USAGE = "get_player_inventory_usage";
    private static final String ACTION_INVENTORY = "get_player_inventory";
    private static final String ACTION_ENDER_CHEST = "get_player_ender_chest";
    private static final String ACTION_ARMOR = "get_player_armor";
    private static final String ACTION_OPEN_INVENTORY = "get_player_open_inventory";
    private static final String ACTION_OPEN_CONTAINER = "get_player_open_container";

    private static final Set<String> PROBEABLE_ACTIONS = Set.of(
            ACTION_HAND_ITEM, ACTION_OFFHAND_ITEM, ACTION_INVENTORY_USAGE, ACTION_INVENTORY,
            ACTION_ENDER_CHEST, ACTION_ARMOR, ACTION_OPEN_INVENTORY, ACTION_OPEN_CONTAINER);

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    protected String getLogPrefix() {
        return LOG_PREFIX;
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.API_PLAYER_INVENTORY.getNode();
    }

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    @Override
    protected SkillResult executeActions(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case ACTION_HAND_ITEM -> getHandItem(player);
            case ACTION_OFFHAND_ITEM -> getOffhandItem(player);
            case ACTION_INVENTORY_USAGE -> getInventoryUsage(player);
            case ACTION_INVENTORY -> getInventory(player);
            case ACTION_ENDER_CHEST -> getEnderChest(player);
            case ACTION_ARMOR -> getArmor(player);
            case ACTION_OPEN_INVENTORY -> getOpenInventory(player);
            case ACTION_OPEN_CONTAINER -> getOpenContainer(player);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 主手物品（method_chain: getInventory→getItemInMainHand，异步安全，IO 线程直接调）
     *
     * <p>Folia 路径：ItemStack 经 extractThreadSafeData 提取为 Map，formatItemStackFromMap 格式化；
     * Spigot 路径：ItemStack 经 putItemStackFields 提取字段，formatSingleItemStack 格式化。</p>
     */
    private SkillResult getHandItem(Player player) {
        String label = I18nService.tr("主手物品");
        ItemStack item = player.getInventory().getItemInMainHand();
        return buildSingleItemResult(item, label, ACTION_HAND_ITEM);
    }

    /**
     * 副手物品（method_chain: getInventory→getItemInOffHand）
     */
    private SkillResult getOffhandItem(Player player) {
        String label = I18nService.tr("副手物品");
        ItemStack item = player.getInventory().getItemInOffHand();
        return buildSingleItemResult(item, label, ACTION_OFFHAND_ITEM);
    }

    /**
     * 主手/副手物品的通用结果构建（双路径复现原 extractThreadSafeData + formatItemStackFromMap /
     * extractDataFromResult + formatSingleItemStack）。
     */
    private SkillResult buildSingleItemResult(ItemStack item, String label, String apiId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", item);
        dataMap.put("api_id", apiId);

        String message;
        if (FoliaCompat.isFolia()) {
            // Folia 路径：extractThreadSafeData(ItemStack) 提取为 Map
            if (item == null || item.getType() == Material.AIR) {
                message = label + I18nService.tr("：空手");
            } else {
                Map<String, Object> itemMap = new HashMap<>();
                BukkitAPIResultFormatter.putItemStackFieldsFolia(item, itemMap);
                // extractThreadSafeData 返回的 Map 字段在 execute() 通用逻辑中展开进 dataMap
                for (Map.Entry<String, Object> entry : itemMap.entrySet()) {
                    dataMap.put(entry.getKey(), entry.getValue());
                }
                message = BukkitAPIResultFormatter.formatItemStackFromMap(label, itemMap);
            }
        } else {
            // Spigot 路径：extractDataFromResult(ItemStack) 直接填 dataMap
            if (item == null || item.getType() == Material.AIR) {
                message = label + I18nService.tr("：空手");
            } else {
                BukkitAPIResultFormatter.putItemStackFields(item, dataMap);
                message = BukkitAPIResultFormatter.formatSingleItemStack(label, item);
            }
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 背包占用（method_chain: getInventory→getStorageContents，getStorageContents 需主线程）
     */
    private SkillResult getInventoryUsage(Player player) {
        String label = I18nService.tr("背包");
        ItemStack[] contents = FoliaCompat.callSync(KilacraftAI.getInstance(),
                () -> player.getInventory().getStorageContents(), 5);
        return buildInventoryUsageResult(contents, label, ACTION_INVENTORY_USAGE);
    }

    /**
     * 背包物品摘要（同 getStorageContents 主线程调度，额外提取物品列表）
     */
    private SkillResult getInventory(Player player) {
        String label = I18nService.tr("背包");
        ItemStack[] contents = FoliaCompat.callSync(KilacraftAI.getInstance(),
                () -> player.getInventory().getStorageContents(), 5);
        return buildInventorySummaryResult(contents, label, ACTION_INVENTORY);
    }

    /**
     * 末影箱物品摘要（method_chain: getEnderChest→getStorageContents，目标=EnderChest → callSync）
     */
    private SkillResult getEnderChest(Player player) {
        String label = I18nService.tr("末影箱");
        ItemStack[] contents = FoliaCompat.callSync(KilacraftAI.getInstance(),
                () -> player.getEnderChest().getStorageContents(), 5);
        return buildInventorySummaryResult(contents, label, ACTION_ENDER_CHEST);
    }

    /**
     * 背包占用结果构建（双路径：Folia 走 formatInventoryFromMap 极轻量模式；Spigot 走 formatInventoryUsage）
     */
    private SkillResult buildInventoryUsageResult(ItemStack[] contents, String label, String apiId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", apiId);

        String message;
        if (FoliaCompat.isFolia()) {
            // Folia 路径：extractThreadSafeData(ItemStack[], "getStorageContents") 返回 {item_count, empty_slots}（无 items）
            Map<String, Object> invMap = new HashMap<>();
            BukkitAPIResultFormatter.putInventoryUsageFields(contents, invMap);
            dataMap.put("raw_result", invMap);
            for (Map.Entry<String, Object> entry : invMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatInventoryFromMap(label, true, invMap);
        } else {
            // Spigot 路径：extractInventoryUsage 填 dataMap；formatInventoryUsage 格式化
            dataMap.put("raw_result", contents);
            BukkitAPIResultFormatter.putInventoryUsageFields(contents, dataMap);
            message = BukkitAPIResultFormatter.formatInventoryUsage(contents, label);
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 背包/末影箱/容器物品摘要结果构建（双路径）
     */
    private SkillResult buildInventorySummaryResult(ItemStack[] contents, String label, String apiId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", apiId);

        String message;
        if (FoliaCompat.isFolia()) {
            // Folia 路径：extractThreadSafeData(ItemStack[], "getStorageContents") 返回 {item_count, empty_slots, items}
            Map<String, Object> invMap = new HashMap<>();
            BukkitAPIResultFormatter.putInventoryUsageFields(contents, invMap);
            BukkitAPIResultFormatter.putInventorySummaryFields(contents, invMap);
            dataMap.put("raw_result", invMap);
            for (Map.Entry<String, Object> entry : invMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatInventoryFromMap(label, false, invMap);
        } else {
            // Spigot 路径：extractInventoryUsage + extractInventorySummary 填 dataMap；formatInventorySummary 格式化
            dataMap.put("raw_result", contents);
            BukkitAPIResultFormatter.putInventoryUsageFields(contents, dataMap);
            BukkitAPIResultFormatter.putInventorySummaryFields(contents, dataMap);
            message = BukkitAPIResultFormatter.formatInventorySummary(contents, label);
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 盔甲装备（method_chain: getInventory→getArmorContents，getArmorContents 需主线程）
     */
    private SkillResult getArmor(Player player) {
        ItemStack[] armor = FoliaCompat.callSync(KilacraftAI.getInstance(),
                () -> player.getInventory().getArmorContents(), 5);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", ACTION_ARMOR);

        String message;
        if (FoliaCompat.isFolia()) {
            // Folia 路径：extractThreadSafeData(ItemStack[], "getArmorContents") 返回按槽位的 Map（含 empty 标记）
            Map<String, Object> armorMap = new HashMap<>();
            BukkitAPIResultFormatter.putArmorFieldsFolia(armor, armorMap);
            dataMap.put("raw_result", armorMap);
            for (Map.Entry<String, Object> entry : armorMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatArmorFromMap(armorMap);
        } else {
            // Spigot 路径：extractDataFromResult(ItemStack[], get_player_armor) 按槽位填 dataMap；formatArmorContents 格式化
            dataMap.put("raw_result", armor);
            BukkitAPIResultFormatter.putArmorFields(armor, dataMap);
            message = BukkitAPIResultFormatter.formatArmorContents(armor);
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 当前打开的界面类型（method_chain: getOpenInventory→getType，getType 异步安全，IO 线程直接调）
     */
    private SkillResult getOpenInventory(Player player) {
        InventoryType inventoryType = player.getOpenInventory().getType();

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", inventoryType);
        dataMap.put("api_id", ACTION_OPEN_INVENTORY);

        String message = BukkitAPIResultFormatter.formatInventoryType(inventoryType);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 打开的容器内容（method_chain: getOpenInventory→getTopInventory→getStorageContents，需主线程）
     *
     * <p>额外注入 container_type/container_title（原 execute() 的 open_container 特殊分支）。</p>
     */
    private SkillResult getOpenContainer(Player player) {
        ItemStack[] contents = FoliaCompat.callSync(KilacraftAI.getInstance(),
                () -> player.getOpenInventory().getTopInventory().getStorageContents(), 5);

        // 容器标签：取界面类型中文名（去掉「当前界面：」前缀），失败回退「容器」
        String containerLabel = I18nService.tr("容器");
        try {
            InventoryType invType = player.getOpenInventory().getType();
            containerLabel = BukkitAPIResultFormatter.formatInventoryType(invType).replace(I18nService.tr("当前界面："), "");
        } catch (Exception ignored) {
        }

        SkillResult result = buildInventorySummaryResult(contents, containerLabel, ACTION_OPEN_CONTAINER);

        // 额外注入容器元数据（原 execute() 的 get_player_open_container 特殊分支）
        try {
            Map<String, Object> data = result.getDataMap();
            if (data != null) {
                var openInv = player.getOpenInventory();
                data.put("container_type", openInv.getType().name());
                data.put("container_title", openInv.getTitle());
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
