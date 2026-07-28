package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Map;

/**
 * 实体类型名映射，供守护告警模板渲染。
 *
 * <p>以中文原文为 i18n key（{@link I18nService#tr(String)}），英文译名在 messages_en.yml。
 * 问候系统 {@code GreetingPromptBuilder.formatEntityName} 同模式。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-11
 */
public final class EntityNameI18n {

    private EntityNameI18n() {
    }

    private static final Map<EntityType, String> NAMES = Map.ofEntries(
            Map.entry(EntityType.CREEPER, "苦力怕"),
            Map.entry(EntityType.ZOMBIE, "僵尸"),
            Map.entry(EntityType.ZOMBIE_VILLAGER, "僵尸村民"),
            Map.entry(EntityType.SKELETON, "骷髅"),
            Map.entry(EntityType.STRAY, "流浪者"),
            Map.entry(EntityType.WITHER_SKELETON, "凋灵骷髅"),
            Map.entry(EntityType.SPIDER, "蜘蛛"),
            Map.entry(EntityType.CAVE_SPIDER, "洞穴蜘蛛"),
            Map.entry(EntityType.WITCH, "女巫"),
            Map.entry(EntityType.ENDERMAN, "末影人"),
            Map.entry(EntityType.ENDER_DRAGON, "末影龙"),
            Map.entry(EntityType.WITHER, "凋灵"),
            Map.entry(EntityType.BLAZE, "烈焰人"),
            Map.entry(EntityType.GHAST, "恶魂"),
            Map.entry(EntityType.MAGMA_CUBE, "岩浆怪"),
            Map.entry(EntityType.SLIME, "史莱姆"),
            Map.entry(EntityType.PHANTOM, "幻翼"),
            Map.entry(EntityType.GUARDIAN, "守卫者"),
            Map.entry(EntityType.ELDER_GUARDIAN, "远古守卫者"),
            Map.entry(EntityType.SHULKER, "潜影贝"),
            Map.entry(EntityType.PILLAGER, "掠夺者"),
            Map.entry(EntityType.VINDICATOR, "卫道士"),
            Map.entry(EntityType.EVOKER, "唤魔者"),
            Map.entry(EntityType.RAVAGER, "劫掠兽"),
            Map.entry(EntityType.VEX, "恼鬼"),
            Map.entry(EntityType.HOGLIN, "疣猪兽"),
            Map.entry(EntityType.ZOGLIN, "僵尸疣猪兽"),
            Map.entry(EntityType.PIGLIN, "猪灵"),
            Map.entry(EntityType.PIGLIN_BRUTE, "猪灵蛮兵"),
            Map.entry(EntityType.STRIDER, "炽足兽"),
            Map.entry(EntityType.SKELETON_HORSE, "骷髅马"),
            Map.entry(EntityType.ENDERMITE, "末影螨"),
            Map.entry(EntityType.SILVERFISH, "蠹虫"),
            Map.entry(EntityType.DROWNED, "溺尸"),
            Map.entry(EntityType.HUSK, "尸壳")
    );

    /**
     * 返回实体类型的本地化名，未收录则退化为可读英文名（小写替换下划线）。
     */
    public static String name(EntityType type) {
        if (type == null) {
            return I18nService.tr("未知生物");
        }
        String name = NAMES.get(type);
        if (name != null) {
            return I18nService.tr(name);
        }
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
