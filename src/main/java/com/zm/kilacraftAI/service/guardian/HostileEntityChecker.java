package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.EntityType;

import java.util.Set;

/**
 * 实体类型分类工具：判定敌对生物。
 *
 * <p>Bukkit 的 {@code Monster} 接口覆盖大部分敌对生物，但 Slime/Phantom/Guardian 等非 Monster
 * 敌对实体需单独枚举。中立生物（末影人/僵尸猪人等）单独标记——它们只在被激怒时才构成威胁，
 * EntityTargetEvent 的 wander/瞬时锁定不应触发威胁告警。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-11
 */
public final class HostileEntityChecker {

    private HostileEntityChecker() {
    }

    private static final Set<EntityType> HOSTILE_NON_MONSTER = Set.of(EntityType.SLIME, EntityType.PHANTOM, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SHULKER, EntityType.ENDERMITE, EntityType.SILVERFISH);

    /**
     * Monster 接口的子类但平时中立的实体——它们只在被激怒时才真正构成威胁。
     * EntityTargetEvent 可能因 wander/瞬时锁定而触发，若不排除会导致"狼来了"式误报。
     */
    private static final Set<EntityType> NEUTRAL_MONSTERS = Set.of(EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.POLAR_BEAR, EntityType.BEE);

    private static volatile Class<?> enemyClass;
    private static volatile Class<?> monsterClass;

    public static boolean isHostile(EntityType type) {
        if (type == null) {
            return false;
        }
        // 中立生物排除——它们不主动威胁玩家，避免 EntityTargetEvent wander 时的误报
        if (NEUTRAL_MONSTERS.contains(type)) {
            return false;
        }
        if (HOSTILE_NON_MONSTER.contains(type)) {
            return true;
        }
        Class<?> entityClass = type.getEntityClass();
        if (entityClass == null) {
            return false;
        }
        return isMonsterSubclass(entityClass) || isEnemySubclass(entityClass);
    }

    private static boolean isMonsterSubclass(Class<?> entityClass) {
        if (monsterClass == null) {
            try {
                monsterClass = Class.forName("org.bukkit.entity.Monster");
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return monsterClass.isAssignableFrom(entityClass);
    }

    /**
     * Enemy 接口（1.17+）：覆盖 Monster + EnderDragon 等非 Monster 敌对实体。Boss 接口已在 1.17 移除。
     */
    private static boolean isEnemySubclass(Class<?> entityClass) {
        if (enemyClass == null) {
            try {
                enemyClass = Class.forName("org.bukkit.entity.Enemy");
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return enemyClass.isAssignableFrom(entityClass);
    }
}
