package com.zm.kilacraftAI.greeting;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Bukkit 原版统计数据采集结果
 *
 * <ul>
 *     <li>Bukkit Stats 为累计统计的权威来源（替代 player_profile 的 totalPlaytimeMs/loginCount）</li>
 *     <li>player_profile 仅用于内部逻辑（last_login/last_logout），不用于统计展示</li>
 *     <li>游戏时长单位：分钟（采集时从 tick 自动换算，1200 tick = 1 分钟）</li>
 *     <li>距离单位统一为"格"（采集时从 cm 换算，1格=100cm）</li>
 *     <li>伤害单位为"半心"（展示层由 GreetingPromptBuilder 转为"颗心"）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public record PlayerVanillaStats(
        // === 基础/战斗（10项）===
        int playMinutes,                // PLAY_ONE_MINUTE
        int deaths,                     // DEATHS
        int mobKills,                   // MOB_KILLS
        int playerKills,                // PLAYER_KILLS
        int damageDealt,                // DAMAGE_DEALT（半心）
        int damageTaken,                // DAMAGE_TAKEN（半心）
        int damageShielded,             // DAMAGE_BLOCKED_BY_SHIELD（半心）
        int animalsBred,                // ANIMALS_BRED
        int jumps,                      // JUMP
        int sleepCount,                 // SLEEP_IN_BED

        // === 稀有BOSS（7项）===
        int dragonKills,                // KILL_ENTITY(ENDER_DRAGON)
        int dragonDeaths,               // ENTITY_KILLED_BY(ENDER_DRAGON)
        int witherKills,                // KILL_ENTITY(WITHER)
        int witherDeaths,               // ENTITY_KILLED_BY(WITHER)
        int elderGuardianKills,         // KILL_ENTITY(ELDER_GUARDIAN)
        int wardenKills,                // KILL_ENTITY(WARDEN)
        int ironGolemKills,             // KILL_ENTITY(IRON_GOLEM)

        // === 探索/距离（10项，单位cm）===
        int walkCm,                     // WALK_ONE_CM
        int sprintCm,                   // SPRINT_ONE_CM
        int flyCm,                      // FLY_ONE_CM
        int elytraCm,                   // AVIATE_ONE_CM
        int swimCm,                     // SWIM_ONE_CM
        int boatCm,                     // BOAT_ONE_CM
        int minecartCm,                 // MINECART_ONE_CM
        int horseCm,                    // HORSE_ONE_CM
        int climbCm,                    // CLIMB_ONE_CM
        int fallCm,                     // FALL_ONE_CM

        // === 生活/趣味（5项）===
        int fishCaught,                 // FISH_CAUGHT
        int enchantCount,               // ITEM_ENCHANTED
        int raidTriggered,              // RAID_TRIGGER
        int raidWon,                    // RAID_WIN
        int diamondOreMined             // MINE_BLOCK(DIAMOND_ORE)
) {

    /**
     * 空统计（采集失败时降级使用）
     */
    public static PlayerVanillaStats empty() {
        return new PlayerVanillaStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * 从 Player 对象采集全部 32 项 Stat
     *
     * @param player 玩家对象
     * @return 采集结果（任何单项采集失败降级为0）
     */
    public static PlayerVanillaStats collect(Player player) {
        return new PlayerVanillaStats(
                // 基础/战斗
                tickToMinutes(safeGet(player, Statistic.PLAY_ONE_MINUTE)), safeGet(player, Statistic.DEATHS), safeGet(player, Statistic.MOB_KILLS), safeGet(player, Statistic.PLAYER_KILLS), safeGet(player, Statistic.DAMAGE_DEALT), safeGet(player, Statistic.DAMAGE_TAKEN), safeGet(player, Statistic.DAMAGE_BLOCKED_BY_SHIELD), safeGet(player, Statistic.ANIMALS_BRED), safeGet(player, Statistic.JUMP), safeGet(player, Statistic.SLEEP_IN_BED),
                // 稀有BOSS
                safeGetEntity(player, Statistic.KILL_ENTITY, EntityType.ENDER_DRAGON), safeGetEntity(player, Statistic.ENTITY_KILLED_BY, EntityType.ENDER_DRAGON), safeGetEntity(player, Statistic.KILL_ENTITY, EntityType.WITHER), safeGetEntity(player, Statistic.ENTITY_KILLED_BY, EntityType.WITHER), safeGetEntityByName(player, Statistic.KILL_ENTITY, "ELDER_GUARDIAN"), safeGetEntityByName(player, Statistic.KILL_ENTITY, "WARDEN"), safeGetEntityByName(player, Statistic.KILL_ENTITY, "IRON_GOLEM"),
                // 探索/距离
                safeGet(player, Statistic.WALK_ONE_CM), safeGet(player, Statistic.SPRINT_ONE_CM), safeGet(player, Statistic.FLY_ONE_CM), safeGet(player, Statistic.AVIATE_ONE_CM), safeGet(player, Statistic.SWIM_ONE_CM), safeGet(player, Statistic.BOAT_ONE_CM), safeGet(player, Statistic.MINECART_ONE_CM), safeGet(player, Statistic.HORSE_ONE_CM), safeGet(player, Statistic.CLIMB_ONE_CM), safeGet(player, Statistic.FALL_ONE_CM),
                // 生活/趣味
                safeGet(player, Statistic.FISH_CAUGHT), safeGet(player, Statistic.ITEM_ENCHANTED), safeGet(player, Statistic.RAID_TRIGGER), safeGet(player, Statistic.RAID_WIN), safeGetBlock(player, Statistic.MINE_BLOCK, Material.DIAMOND_ORE));
    }

    private static int tickToMinutes(int ticks) {
        return ticks / 1200;
    }

    private static int safeGet(Player p, Statistic stat) {
        try {
            return p.getStatistic(stat);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int safeGetEntity(Player p, Statistic stat, EntityType type) {
        try {
            return p.getStatistic(stat, type);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int safeGetBlock(Player p, Statistic stat, Material mat) {
        try {
            return p.getStatistic(stat, mat);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 动态解析 EntityType，兼容不同版本（编译 API 1.16.5 不含 WARDEN/IRON_GOLEM 等）
     */
    private static EntityType resolveEntityType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 动态解析 EntityType 版本的 safeGetEntity，EntityType 为 null 时直接返回 0
     */
    private static int safeGetEntityByName(Player p, Statistic stat, String entityTypeName) {
        EntityType type = resolveEntityType(entityTypeName);
        if (type == null) return 0;
        return safeGetEntity(p, stat, type);
    }
}
