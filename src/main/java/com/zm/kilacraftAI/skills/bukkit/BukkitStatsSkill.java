package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.knowledge.InternalEnumRegistry;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit 原版统计数据查询 Skill
 *
 * @author Zm_Mmm
 * @since 2026-04-19
 */
public class BukkitStatsSkill implements Skill, ProbeSource {

    private static final String SKILL_NAME = "bukkit_stats";
    private static final String LOG_PREFIX = "原版统计";

    private static final String ACTION_QUERY_STATISTIC = "query_statistic";

    private static final Set<String> PROBEABLE_ACTIONS = Set.of(ACTION_QUERY_STATISTIC);

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    /**
     * 距离统计后缀（厘米），需要自动转换单位
     */
    private static final Set<String> DISTANCE_STATS = Set.of("WALK_ONE_CM", "WALK_ON_WATER_ONE_CM", "WALK_UNDER_WATER_ONE_CM", "FALL_ONE_CM", "CLIMB_ONE_CM", "FLY_ONE_CM", "SPRINT_ONE_CM", "CROUCH_ONE_CM", "SWIM_ONE_CM", "AVIATE_ONE_CM", "MINECART_ONE_CM", "BOAT_ONE_CM", "PIG_ONE_CM", "HORSE_ONE_CM", "STRIDER_ONE_CM");

    /**
     * 时长统计后缀（tick），需要自动转换时间单位
     */
    private static final Set<String> TIME_STATS = Set.of("PLAY_ONE_MINUTE", "TIME_SINCE_DEATH", "TIME_SINCE_REST", "SNEAK_TIME");

    private final SkillConfigManager configManager;

    public BukkitStatsSkill() {
        this.configManager = SkillConfigManager.getInstance();

        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig(this);
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null && !config.getActionDescriptions().isEmpty()) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.BUKKIT_STATS.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        if (!PluginPermissionEnum.BUKKIT_STATS.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.BUKKIT_STATS.getNode())));
        }

        Map<String, String> entities = context.getEntities();

        try {
            // 统计查询可能需要访问玩家数据，在主线程/区域线程执行
            if (FoliaCompat.isPrimaryThread()) {
                return CompletableFuture.completedFuture(executeSync(action, player, entities));
            } else {
                CompletableFuture<SkillResult> future = new CompletableFuture<>();
                FoliaCompat.runTask(KilacraftAI.getInstance(), () -> {
                    try {
                        SkillResult result = executeSync(action, player, entities);
                        future.complete(result);
                    } catch (Exception e) {
                        future.complete(SkillResult.failure(I18nService.tr("执行失败: {}", e.getMessage())));
                    }
                });
                return future;
            }
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, "执行统计查询失败", e);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("执行失败: {}", e.getMessage())));
        }
    }

    private SkillResult executeSync(String action, Player player, Map<String, String> entities) {
        if (!ACTION_QUERY_STATISTIC.equals(action)) {
            return SkillResult.failure(I18nService.tr("未知动作: {}", action));
        }
        return queryStatistic(player, entities);
    }

    /**
     * 查询统计项
     */
    private SkillResult queryStatistic(Player player, Map<String, String> entities) {
        String statisticName = SkillEntityHelper.getString(entities, "statistic");
        if (statisticName == null) {
            return SkillResult.needInfo(I18nService.tr("要查询哪项统计？请告诉我统计项的英文名或中文描述（如：DEATHS、死亡次数、MOB_KILLS）。"));
        }

        // 解析统计枚举：精确匹配 → 内置注册表模糊匹配
        Statistic statistic = resolveStatistic(statisticName);
        if (statistic == null) {
            return SkillResult.failure(I18nService.tr("无效的统计枚举名称: {}", statisticName));
        }

        // 根据统计类型调用不同的 getStatistic 重载
        try {
            int value;
            String formattedResult;
            String materialName = null;
            String entityTypeName = null;

            switch (statistic.getType()) {
                case UNTYPED -> {
                    value = player.getStatistic(statistic);
                    formattedResult = formatResult(statistic, value);
                }
                case ITEM -> {
                    materialName = SkillEntityHelper.getString(entities, "material");
                    if (materialName == null) {
                        return SkillResult.needInfo(I18nService.tr("统计项 {} 需要指定物品材质名（如：DIAMOND_SWORD），请告诉我要查询哪种物品。", statisticName));
                    }
                    Material material = parseMaterial(materialName);
                    if (material == null || !material.isItem()) {
                        return SkillResult.failure(I18nService.tr("无效的物品材质名: {}", materialName));
                    }
                    value = player.getStatistic(statistic, material);
                    formattedResult = formatResult(statistic, value, materialName);
                }
                case BLOCK -> {
                    materialName = SkillEntityHelper.getString(entities, "material");
                    if (materialName == null) {
                        return SkillResult.needInfo(I18nService.tr("统计项 {} 需要指定方块材质名（如：DIAMOND_ORE），请告诉我要查询哪种方块。", statisticName));
                    }
                    Material material = parseMaterial(materialName);
                    if (material == null || !material.isBlock()) {
                        return SkillResult.failure(I18nService.tr("无效的方块材质名: {}", materialName));
                    }
                    value = player.getStatistic(statistic, material);
                    formattedResult = formatResult(statistic, value, materialName);
                }
                case ENTITY -> {
                    entityTypeName = SkillEntityHelper.getString(entities, "entity_type");
                    if (entityTypeName == null) {
                        return SkillResult.needInfo(I18nService.tr("统计项 {} 需要指定实体类型名（如：ZOMBIE），请告诉我要查询哪种实体。", statisticName));
                    }
                    EntityType entityType = parseEntityType(entityTypeName);
                    if (entityType == null) {
                        return SkillResult.failure(I18nService.tr("无效的实体类型名: {}", entityTypeName));
                    }
                    value = player.getStatistic(statistic, entityType);
                    formattedResult = formatResult(statistic, value, entityTypeName);
                }
                default -> {
                    return SkillResult.failure(I18nService.tr("不支持的统计类型: {}", statistic.getType()));
                }
            }

            // 构建 dataMap 供多步骤任务引用
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("statistic", statisticName);
            dataMap.put("value", value);
            dataMap.put("statistic_type", statistic.getType().name());
            if (statistic.getType() == Statistic.Type.ITEM || statistic.getType() == Statistic.Type.BLOCK) {
                dataMap.put("material", materialName);
            } else if (statistic.getType() == Statistic.Type.ENTITY) {
                dataMap.put("entity_type", entityTypeName);
            }

            return SkillResult.success(formattedResult, dataMap);
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("查询统计失败: {}", statisticName), e);
            return SkillResult.failure(I18nService.tr("查询统计失败: {}", e.getMessage()));
        }
    }

    /**
     * 格式化无参数统计结果
     */
    private String formatResult(Statistic statistic, int value) {
        String name = statistic.name();

        // 距离类：厘米 → 可读距离
        if (DISTANCE_STATS.contains(name)) {
            return formatDistance(value, getStatDisplayName(name));
        }

        // 时长类：tick → 可读时间
        if (TIME_STATS.contains(name)) {
            return formatTimeTicks(value, getStatDisplayName(name));
        }

        // 通用格式
        return getStatDisplayName(name) + ": " + value;
    }

    /**
     * 格式化带参数统计结果
     */
    private String formatResult(Statistic statistic, int value, String param) {
        String name = statistic.name();
        String displayName = getStatDisplayName(name);
        String paramDisplay = formatParamName(param);

        // 距离类：厘米 → 可读距离
        if (DISTANCE_STATS.contains(name)) {
            return formatDistance(value, displayName + " (" + paramDisplay + ")");
        }

        return displayName + " (" + paramDisplay + "): " + value;
    }

    /**
     * 格式化距离值（厘米 → 米/公里）
     */
    private String formatDistance(int cm, String label) {
        double meters = cm / 100.0;
        if (meters >= 1000) {
            return I18nService.tr("{}: {} 公里", label, String.format("%.1f", meters / 1000));
        } else {
            return I18nService.tr("{}: {} 米", label, String.format("%.1f", meters));
        }
    }

    /**
     * 格式化时间值（tick → 可读时间）
     */
    private String formatTimeTicks(int ticks, String label) {
        long seconds = ticks / 20;
        if (seconds < 60) {
            return I18nService.tr("{}: {} 秒", label, seconds);
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainSeconds = seconds % 60;
            return I18nService.tr("{}: {} 分 {} 秒", label, minutes, remainSeconds);
        } else {
            long hours = seconds / 3600;
            long remainMinutes = (seconds % 3600) / 60;
            return I18nService.tr("{}: {} 小时 {} 分", label, hours, remainMinutes);
        }
    }

    /**
     * 获取统计项的中文显示名
     */
    private String getStatDisplayName(String statName) {
        if (I18nService.isZh()) {
            return switch (statName) {
                case "DEATHS" -> "总死亡次数";
                case "PLAYER_KILLS" -> "击杀玩家数";
                case "MOB_KILLS" -> "击杀生物数";
                case "FISH_CAUGHT" -> "钓鱼数";
                case "ANIMALS_BRED" -> "繁殖动物数";
                case "LEAVE_GAME" -> "离开游戏次数";
                case "JUMP" -> "跳跃次数";
                case "DROP_COUNT" -> "丢弃次数";
                case "DROP" -> "丢弃物品数";
                case "PICKUP" -> "拾取物品数";
                case "PLAY_ONE_MINUTE" -> "游戏总时长";
                case "TIME_SINCE_DEATH" -> "距上次死亡";
                case "TIME_SINCE_REST" -> "距上次睡觉";
                case "SNEAK_TIME" -> "潜行时长";
                case "DAMAGE_DEALT" -> "造成总伤害";
                case "DAMAGE_TAKEN" -> "受到总伤害";
                case "DAMAGE_ABSORBED" -> "吸收伤害";
                case "DAMAGE_BLOCKED_BY_SHIELD" -> "盾牌格挡伤害";
                case "DAMAGE_DEALT_ABSORBED" -> "造成被吸收伤害";
                case "DAMAGE_DEALT_RESISTED" -> "造成被减免伤害";
                case "DAMAGE_RESISTED" -> "抗性减免伤害";
                case "WALK_ONE_CM" -> "行走距离";
                case "WALK_ON_WATER_ONE_CM" -> "水面行走距离";
                case "WALK_UNDER_WATER_ONE_CM" -> "水下行走距离";
                case "FALL_ONE_CM" -> "摔落距离";
                case "CLIMB_ONE_CM" -> "攀爬距离";
                case "FLY_ONE_CM" -> "飞行距离";
                case "SPRINT_ONE_CM" -> "冲刺距离";
                case "CROUCH_ONE_CM" -> "潜行移动距离";
                case "SWIM_ONE_CM" -> "游泳距离";
                case "AVIATE_ONE_CM" -> "鞘翅飞行距离";
                case "MINECART_ONE_CM" -> "矿车移动距离";
                case "BOAT_ONE_CM" -> "船移动距离";
                case "PIG_ONE_CM" -> "骑猪移动距离";
                case "HORSE_ONE_CM" -> "骑马移动距离";
                case "STRIDER_ONE_CM" -> "骑炽足兽距离";
                case "MINE_BLOCK" -> "挖掘方块";
                case "CRAFT_ITEM" -> "合成物品";
                case "USE_ITEM" -> "使用物品";
                case "BREAK_ITEM" -> "用坏物品";
                case "KILL_ENTITY" -> "击杀生物";
                case "ENTITY_KILLED_BY" -> "被生物击杀";
                case "CAKE_SLICES_EATEN" -> "吃蛋糕片数";
                case "ARMOR_CLEANED" -> "清洗盔甲次数";
                case "BANNER_CLEANED" -> "清洗旗帜次数";
                case "ITEM_ENCHANTED" -> "附魔次数";
                case "SLEEP_IN_BED" -> "睡觉次数";
                case "CHEST_OPENED" -> "打开箱子次数";
                case "ENDERCHEST_OPENED" -> "打开末影箱次数";
                case "SHULKER_BOX_OPENED" -> "打开潜影盒次数";
                case "FURNACE_INTERACTION" -> "使用熔炉次数";
                case "CRAFTING_TABLE_INTERACTION" -> "使用工作台次数";
                case "BREWINGSTAND_INTERACTION" -> "使用酿造台次数";
                case "BEACON_INTERACTION" -> "使用信标次数";
                case "RAID_TRIGGER" -> "触发袭击次数";
                case "RAID_WIN" -> "袭击胜利次数";
                case "BELL_RING" -> "敲钟次数";
                default -> statName;
            };
        }
        return switch (statName) {
            case "DEATHS" -> "Total deaths";
            case "PLAYER_KILLS" -> "Player kills";
            case "MOB_KILLS" -> "Mob kills";
            case "FISH_CAUGHT" -> "Fish caught";
            case "ANIMALS_BRED" -> "Animals bred";
            case "LEAVE_GAME" -> "Leave game count";
            case "JUMP" -> "Jump count";
            case "DROP_COUNT" -> "Drop count";
            case "DROP" -> "Items dropped";
            case "PICKUP" -> "Items picked up";
            case "PLAY_ONE_MINUTE" -> "Total playtime";
            case "TIME_SINCE_DEATH" -> "Time since last death";
            case "TIME_SINCE_REST" -> "Time since last rest";
            case "SNEAK_TIME" -> "Sneak time";
            case "DAMAGE_DEALT" -> "Total damage dealt";
            case "DAMAGE_TAKEN" -> "Total damage taken";
            case "DAMAGE_ABSORBED" -> "Damage absorbed";
            case "DAMAGE_BLOCKED_BY_SHIELD" -> "Damage blocked by shield";
            case "DAMAGE_DEALT_ABSORBED" -> "Damage dealt (absorbed)";
            case "DAMAGE_DEALT_RESISTED" -> "Damage dealt (resisted)";
            case "DAMAGE_RESISTED" -> "Damage resisted";
            case "WALK_ONE_CM" -> "Walk distance";
            case "WALK_ON_WATER_ONE_CM" -> "Walk on water distance";
            case "WALK_UNDER_WATER_ONE_CM" -> "Walk underwater distance";
            case "FALL_ONE_CM" -> "Fall distance";
            case "CLIMB_ONE_CM" -> "Climb distance";
            case "FLY_ONE_CM" -> "Fly distance";
            case "SPRINT_ONE_CM" -> "Sprint distance";
            case "CROUCH_ONE_CM" -> "Crouch distance";
            case "SWIM_ONE_CM" -> "Swim distance";
            case "AVIATE_ONE_CM" -> "Elytra fly distance";
            case "MINECART_ONE_CM" -> "Minecart distance";
            case "BOAT_ONE_CM" -> "Boat distance";
            case "PIG_ONE_CM" -> "Pig distance";
            case "HORSE_ONE_CM" -> "Horse distance";
            case "STRIDER_ONE_CM" -> "Strider distance";
            case "MINE_BLOCK" -> "Blocks mined";
            case "CRAFT_ITEM" -> "Items crafted";
            case "USE_ITEM" -> "Items used";
            case "BREAK_ITEM" -> "Items broken";
            case "KILL_ENTITY" -> "Entities killed";
            case "ENTITY_KILLED_BY" -> "Killed by entities";
            case "CAKE_SLICES_EATEN" -> "Cake slices eaten";
            case "ARMOR_CLEANED" -> "Armor cleaned";
            case "BANNER_CLEANED" -> "Banners cleaned";
            case "ITEM_ENCHANTED" -> "Items enchanted";
            case "SLEEP_IN_BED" -> "Times slept in bed";
            case "CHEST_OPENED" -> "Chests opened";
            case "ENDERCHEST_OPENED" -> "Ender chests opened";
            case "SHULKER_BOX_OPENED" -> "Shulker boxes opened";
            case "FURNACE_INTERACTION" -> "Furnace interactions";
            case "CRAFTING_TABLE_INTERACTION" -> "Crafting table interactions";
            case "BREWINGSTAND_INTERACTION" -> "Brewing stand interactions";
            case "BEACON_INTERACTION" -> "Beacon interactions";
            case "RAID_TRIGGER" -> "Raids triggered";
            case "RAID_WIN" -> "Raids won";
            case "BELL_RING" -> "Bells rung";
            default -> statName;
        };
    }

    /**
     * 格式化参数名（material/entity_type 的枚举名 → 可读名称）
     */
    private String formatParamName(String paramName) {
        // 尝试翻译 Material 名
        try {
            Material mat = Material.valueOf(paramName.toUpperCase());
            return ItemTranslator.getInstance().translateToChinese(mat.name());
        } catch (IllegalArgumentException ignored) {
        }

        // 尝试翻译 EntityType 名
        try {
            EntityType type = EntityType.valueOf(paramName.toUpperCase());
            return translateEntityType(type);
        } catch (IllegalArgumentException ignored) {
        }

        // 兜底：替换下划线为空格
        return paramName.replace("_", " ").toLowerCase();
    }

    /**
     * 翻译 EntityType 为中文名
     */
    private String translateEntityType(EntityType type) {
        if (I18nService.isZh()) {
            return switch (type) {
                case ZOMBIE -> "僵尸";
                case SKELETON -> "骷髅";
                case CREEPER -> "苦力怕";
                case SPIDER -> "蜘蛛";
                case ENDERMAN -> "末影人";
                case ENDER_DRAGON -> "末影龙";
                case WITHER -> "凋灵";
                case WITCH -> "女巫";
                case BLAZE -> "烈焰人";
                case GHAST -> "恶魂";
                case SLIME -> "史莱姆";
                case PHANTOM -> "幻翼";
                case RAVAGER -> "劫掠兽";
                case PILLAGER -> "掠夺者";
                case VINDICATOR -> "卫道士";
                case EVOKER -> "唤魔者";
                case VEX -> "恼鬼";
                case GUARDIAN -> "守卫者";
                case ELDER_GUARDIAN -> "远古守卫者";
                case SILVERFISH -> "蠹虫";
                case ENDERMITE -> "末影螨";
                case CAVE_SPIDER -> "洞穴蜘蛛";
                case HUSK -> "尸壳";
                case STRAY -> "流浪者";
                case DROWNED -> "溺尸";
                case WITHER_SKELETON -> "凋灵骷髅";
                case PIGLIN -> "猪灵";
                case HOGLIN -> "疣猪兽";
                case ZOGLIN -> "僵尸疣猪兽";
                case PIGLIN_BRUTE -> "猪灵蛮兵";
                case PLAYER -> "玩家";
                default -> type.name().replace("_", " ").toLowerCase();
            };
        }
        return switch (type) {
            case ZOMBIE -> "Zombie";
            case SKELETON -> "Skeleton";
            case CREEPER -> "Creeper";
            case SPIDER -> "Spider";
            case ENDERMAN -> "Enderman";
            case ENDER_DRAGON -> "Ender Dragon";
            case WITHER -> "Wither";
            case WITCH -> "Witch";
            case BLAZE -> "Blaze";
            case GHAST -> "Ghast";
            case SLIME -> "Slime";
            case PHANTOM -> "Phantom";
            case RAVAGER -> "Ravager";
            case PILLAGER -> "Pillager";
            case VINDICATOR -> "Vindicator";
            case EVOKER -> "Evoker";
            case VEX -> "Vex";
            case GUARDIAN -> "Guardian";
            case ELDER_GUARDIAN -> "Elder Guardian";
            case SILVERFISH -> "Silverfish";
            case ENDERMITE -> "Endermite";
            case CAVE_SPIDER -> "Cave Spider";
            case HUSK -> "Husk";
            case STRAY -> "Stray";
            case DROWNED -> "Drowned";
            case WITHER_SKELETON -> "Wither Skeleton";
            case PIGLIN -> "Piglin";
            case HOGLIN -> "Hoglin";
            case ZOGLIN -> "Zoglin";
            case PIGLIN_BRUTE -> "Piglin Brute";
            case PLAYER -> "Player";
            default -> type.name().replace("_", " ").toLowerCase();
        };
    }

    /**
     * 解析统计枚举：精确 → 内置注册表模糊匹配
     */
    private Statistic resolveStatistic(String input) {
        try {
            return Statistic.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        InternalEnumRegistry registry = InternalEnumRegistry.getInstance();
        if (registry != null) {
            String matched = registry.resolveStatistic(input);
            if (matched != null) {
                try {
                    return Statistic.valueOf(matched);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * 安全解析 Material 枚举
     */
    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 安全解析 EntityType 枚举
     */
    private EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
