package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 玩家实时状态查询 Skill（player.status 域，18 个 action）
 *
 * <p>承载原 {@code GenericBukkitAPISkill} 中权限 {@code kilacraft.api.player.status} 下的全部 API：
 * 生命/饥饿/氧气/经验/睡眠/攻击冷却/着火/冰冻/药水/吸收/箭矢/无敌帧/下落/上次受伤/潜行/冲刺/总经验。
 * 入参/反参/字段名/线程模型/Folia 兼容逐项沿用原实现，零行为回归。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public class BukkitPlayerStatusSkill extends AbstractBukkitQuerySkill {

    private static final String SKILL_NAME = "bukkit_player_status";
    private static final String LOG_PREFIX = "Bukkit状态查询";

    private static final Set<String> PROBEABLE_ACTIONS = Set.of(
            "get_player_health", "get_player_food", "get_player_oxygen", "get_player_exp",
            "get_player_exp_to_level", "get_player_sleep_status", "get_player_attack_cooldown",
            "get_player_fire_status", "get_player_freeze_status", "get_player_potion_effects",
            "get_player_absorption", "get_player_arrows_in_body", "get_player_no_damage_ticks",
            "get_player_fall_distance", "get_player_last_damage", "get_player_sneak_status",
            "get_player_sprint_status", "get_player_total_exp");

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
        return PluginPermissionEnum.API_PLAYER_STATUS.getNode();
    }

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    @Override
    protected SkillResult executeActions(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case "get_player_health" -> getHealth(player);
            case "get_player_food" -> getFood(player);
            case "get_player_oxygen" -> getOxygen(player);
            case "get_player_exp" -> getExp(player);
            case "get_player_exp_to_level" -> getExpToLevel(player);
            case "get_player_sleep_status" -> getSleepStatus(player);
            case "get_player_attack_cooldown" -> getAttackCooldown(player);
            case "get_player_fire_status" -> getFireStatus(player);
            case "get_player_freeze_status" -> getFreezeStatus(player);
            case "get_player_potion_effects" -> getPotionEffects(player);
            case "get_player_absorption" -> getAbsorption(player);
            case "get_player_arrows_in_body" -> getArrowsInBody(player);
            case "get_player_no_damage_ticks" -> getNoDamageTicks(player);
            case "get_player_fall_distance" -> getFallDistance(player);
            case "get_player_last_damage" -> getLastDamage(player);
            case "get_player_sneak_status" -> getSneakStatus(player);
            case "get_player_sprint_status" -> getSprintStatus(player);
            case "get_player_total_exp" -> getTotalExp(player);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 生命值（additional_methods: getHealth/getMaxHealth，模板「生命值：{health}/{max_health}」，%.2f）
     */
    private SkillResult getHealth(Player player) {
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("health", health, "max_health", maxHealth));
        dataMap.put("api_id", "get_player_health");
        dataMap.put("health", health);
        dataMap.put("max_health", maxHealth);
        String message = I18nService.tr("生命值：{}/{}", String.format("%.2f", health), String.format("%.2f", maxHealth));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 饥饿值（additional_methods: getFoodLevel/getSaturation，模板「饱食度：{food_level}/20, 饱和度：{saturation}」）
     */
    private SkillResult getFood(Player player) {
        int foodLevel = player.getFoodLevel();
        float saturation = player.getSaturation();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("food_level", foodLevel, "saturation", saturation));
        dataMap.put("api_id", "get_player_food");
        dataMap.put("food_level", foodLevel);
        dataMap.put("saturation", saturation);
        // 原模板 "饱食度：{food_level}/20, 饱和度：{saturation}"：food_level 是 Integer（toString），saturation 是 Float（%.2f）
        String message = I18nService.tr("饱食度：{}/20, 饱和度：{}", foodLevel, String.format("%.2f", saturation));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 氧气（additional_methods: getRemainingAir/getMaximumAir，模板「氧气：{remaining_air}/{maximum_air} tick」）
     */
    private SkillResult getOxygen(Player player) {
        int remainingAir = player.getRemainingAir();
        int maximumAir = player.getMaximumAir();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("remaining_air", remainingAir, "maximum_air", maximumAir));
        dataMap.put("api_id", "get_player_oxygen");
        dataMap.put("remaining_air", remainingAir);
        dataMap.put("maximum_air", maximumAir);
        String message = I18nService.tr("氧气：{}/{} tick", remainingAir, maximumAir);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 经验值（additional_methods: getExp/getLevel，模板被忽略，Java 硬编码「等级：{level}，经验进度：{%}%」）
     */
    private SkillResult getExp(Player player) {
        float expProgress = player.getExp();
        int level = player.getLevel();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("exp_progress", expProgress, "level", level));
        dataMap.put("api_id", "get_player_exp");
        dataMap.put("exp_progress", expProgress);
        dataMap.put("level", level);
        // exp_progress 小数转百分比
        int percentage = Math.round(expProgress * 100);
        String message = I18nService.tr("等级：{}，经验进度：{}%", level, percentage);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 升级所需经验（method_chain: getExpToLevel，data_field: exp_to_level）
     */
    private SkillResult getExpToLevel(Player player) {
        int expToLevel = player.getExpToLevel();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", expToLevel);
        dataMap.put("api_id", "get_player_exp_to_level");
        dataMap.put("exp_to_level", expToLevel);
        String message = I18nService.tr("升到下一级需要：{} 点经验", expToLevel);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 睡眠状态（additional_methods: isSleeping/getSleepTicks，模板「正在睡觉：{is_sleeping}, 睡眠时间：{sleep_ticks} tick」）
     */
    private SkillResult getSleepStatus(Player player) {
        boolean isSleeping = player.isSleeping();
        int sleepTicks = player.getSleepTicks();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("is_sleeping", isSleeping, "sleep_ticks", sleepTicks));
        dataMap.put("api_id", "get_player_sleep_status");
        dataMap.put("is_sleeping", isSleeping);
        dataMap.put("sleep_ticks", sleepTicks);
        // 布尔经 formatMapValue：zh→是/否，en→Yes/No
        String message = I18nService.tr("正在睡觉：{}, 睡眠时间：{} tick", BukkitAPIResultFormatter.formatBoolean(isSleeping), sleepTicks);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 攻击冷却（method_chain: getAttackCooldown，data_field: attack_cooldown，Java 硬编码百分比）
     */
    private SkillResult getAttackCooldown(Player player) {
        float cooldown = player.getAttackCooldown();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", cooldown);
        dataMap.put("api_id", "get_player_attack_cooldown");
        dataMap.put("attack_cooldown", cooldown);
        int percentage = Math.round(cooldown * 100);
        String message = I18nService.tr("攻击冷却进度：{}%", percentage);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 着火状态（additional_methods: getFireTicks/getMaxFireTicks，模板被忽略，Java 硬编码）
     */
    private SkillResult getFireStatus(Player player) {
        int fireTicks = player.getFireTicks();
        int maxFireTicks = player.getMaxFireTicks();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("fire_ticks", fireTicks, "max_fire_ticks", maxFireTicks));
        dataMap.put("api_id", "get_player_fire_status");
        dataMap.put("fire_ticks", fireTicks);
        dataMap.put("max_fire_ticks", maxFireTicks);
        // fire_ticks<=0 → 未着火；否则换算秒
        String message;
        if (fireTicks <= 0) {
            message = I18nService.tr("着火状态：未着火");
        } else {
            double seconds = fireTicks / 20.0;
            message = I18nService.tr("着火状态：正在燃烧！剩余 {} 秒 ({}{} tick)", String.format("%.1f", seconds), fireTicks, "/" + maxFireTicks);
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 冰冻状态（additional_methods: isFrozen/getFreezeTicks/getMaxFreezeTicks，模板）
     *
     * <p>这三个方法是 1.17+ API，编译期 spigot-api 1.16.5 不含，通过反射调用（与原 executor 一致）。</p>
     */
    private SkillResult getFreezeStatus(Player player) {
        boolean isFrozen = invokeBoolean(player, "isFrozen");
        int freezeTicks = invokeInt(player, "getFreezeTicks");
        int maxFreezeTicks = invokeInt(player, "getMaxFreezeTicks");
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("is_frozen", isFrozen, "freeze_ticks", freezeTicks, "max_freeze_ticks", maxFreezeTicks));
        dataMap.put("api_id", "get_player_freeze_status");
        dataMap.put("is_frozen", isFrozen);
        dataMap.put("freeze_ticks", freezeTicks);
        dataMap.put("max_freeze_ticks", maxFreezeTicks);
        String message = I18nService.tr("是否冰冻：{}, 冰冻程度：{}/{} tick", BukkitAPIResultFormatter.formatBoolean(isFrozen), freezeTicks, maxFreezeTicks);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 药水效果（method_chain: getActivePotionEffects，返回 Collection<PotionEffect>）
     *
     * <p>Folia 路径：extractThreadSafeData 对 PotionEffect 单个对象提取为 Map，但对 Collection 不做转换
     * （Collection 分支只处理 Player 集合），故 Collection<PotionEffect> 原样返回；extractDataFromResult
     * 对 get_player_potion_effects 提取 effects/effect_count。双路一致。</p>
     */
    private SkillResult getPotionEffects(Player player) {
        var effects = player.getActivePotionEffects();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", effects);
        dataMap.put("api_id", "get_player_potion_effects");
        BukkitAPIResultFormatter.putPotionEffectFields(effects, dataMap);
        String message = BukkitAPIResultFormatter.formatPotionEffects(effects);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 吸收之心（method_chain: getAbsorptionAmount，data_field: absorption，Java 硬编码「吸收之心：{:.1f}」）
     */
    private SkillResult getAbsorption(Player player) {
        double absorption = player.getAbsorptionAmount();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", absorption);
        dataMap.put("api_id", "get_player_absorption");
        dataMap.put("absorption", absorption);
        String message = I18nService.tr("吸收之心：{}", String.format("%.1f", absorption));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 身上的箭（method_chain: getArrowsInBody，data_field: arrows_in_body，Java 硬编码「身上的箭矢：{} 支」）
     */
    private SkillResult getArrowsInBody(Player player) {
        int arrows = player.getArrowsInBody();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", arrows);
        dataMap.put("api_id", "get_player_arrows_in_body");
        dataMap.put("arrows_in_body", arrows);
        String message = I18nService.tr("身上的箭矢：{} 支", arrows);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 无敌帧（additional_methods: getNoDamageTicks/getMaximumNoDamageTicks，模板）
     */
    private SkillResult getNoDamageTicks(Player player) {
        int noDamageTicks = player.getNoDamageTicks();
        int maxNoDamageTicks = player.getMaximumNoDamageTicks();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", buildRawMap("no_damage_ticks", noDamageTicks, "max_no_damage_ticks", maxNoDamageTicks));
        dataMap.put("api_id", "get_player_no_damage_ticks");
        dataMap.put("no_damage_ticks", noDamageTicks);
        dataMap.put("max_no_damage_ticks", maxNoDamageTicks);
        String message = I18nService.tr("无敌帧：{}/{} tick", noDamageTicks, maxNoDamageTicks);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 下落距离（method_chain: getFallDistance，data_field: fall_distance，数值无前缀）
     */
    private SkillResult getFallDistance(Player player) {
        float fallDistance = player.getFallDistance();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", fallDistance);
        dataMap.put("api_id", "get_player_fall_distance");
        dataMap.put("fall_distance", fallDistance);
        // 原 formatResult：Float 且非 speed/attack_cooldown/absorption → 走默认 toString
        String message = String.valueOf(fallDistance);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 上次受伤原因（method_chain: getLastDamageCause，getLastDamageCause 需主线程，callSyncOnEntity）
     *
     * <p>复现原 {@code invokeOnMainThread} 的线程内提取：Folia 下在区域线程 lambda 内立即把
     * {@code EntityDamageEvent} 提取为线程安全 Map（避免 IO 线程跨区域访问字段）；Spigot 下返回原始事件。
     * 字段差异：Folia 路径含 {@code final_damage}、{@code damage_cause} 存枚举 name()；
     * Spigot 路径无 {@code final_damage}、{@code damage_cause} 存本地化文案。</p>
     */
    private SkillResult getLastDamage(Player player) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_last_damage");

        if (FoliaCompat.isFolia()) {
            // Folia 路径：在 callSyncOnEntity 的区域线程 lambda 内提取为 Map（线程安全）
            Map<String, Object> damageMap = FoliaCompat.callSyncOnEntity(player, () -> {
                EntityDamageEvent event = player.getLastDamageCause();
                if (event == null) {
                    return null;
                }
                Map<String, Object> map = new HashMap<>();
                BukkitAPIResultFormatter.putDamageFieldsFolia(event, map);
                return map;
            }, 5);
            if (damageMap == null || damageMap.isEmpty()) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            dataMap.put("raw_result", damageMap);
            for (Map.Entry<String, Object> entry : damageMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            String message = BukkitAPIResultFormatter.formatDamageFromMap(damageMap);
            return SkillResult.success(message, dataMap);
        }

        // Spigot 路径：返回原始 EntityDamageEvent，在 IO 线程提取字段（Spigot 单主线程，安全）
        EntityDamageEvent damageEvent = FoliaCompat.callSyncOnEntity(player, () -> player.getLastDamageCause(), 5);
        if (damageEvent == null) {
            dataMap.put("raw_result", null);
            return SkillResult.success(I18nService.tr("无结果"), dataMap);
        }
        dataMap.put("raw_result", damageEvent);
        BukkitAPIResultFormatter.putDamageFields(damageEvent, dataMap);
        String message = BukkitAPIResultFormatter.formatDamageEvent(damageEvent);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 潜行状态（method_chain: isSneaking，data_field: sneaking，Java 硬编码「是/否」）
     */
    private SkillResult getSneakStatus(Player player) {
        boolean sneaking = player.isSneaking();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", sneaking);
        dataMap.put("api_id", "get_player_sneak_status");
        dataMap.put("sneaking", sneaking);
        String message = BukkitAPIResultFormatter.formatBoolean(sneaking);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 冲刺状态（method_chain: isSprinting，data_field: sprinting，Java 硬编码「是/否」）
     */
    private SkillResult getSprintStatus(Player player) {
        boolean sprinting = player.isSprinting();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", sprinting);
        dataMap.put("api_id", "get_player_sprint_status");
        dataMap.put("sprinting", sprinting);
        String message = BukkitAPIResultFormatter.formatBoolean(sprinting);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 总经验（method_chain: getTotalExperience，data_field: total_exp，数值无前缀）
     */
    private SkillResult getTotalExp(Player player) {
        int totalExp = player.getTotalExperience();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", totalExp);
        dataMap.put("api_id", "get_player_total_exp");
        dataMap.put("total_exp", totalExp);
        // 原 formatResult：Integer 非 ping/exp_to_level/arrows → 走默认 toString
        String message = String.valueOf(totalExp);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 反射调用无参 boolean 方法（用于 1.17+ API，编译期 spigot-api 1.16.5 不含）。
     * 方法不存在或调用失败时返回 false（与原 executor 在低版本服务器的退化行为一致）。
     */
    private static boolean invokeBoolean(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            return result instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 反射调用无参 int 方法（用于 1.17+ API）。方法不存在或调用失败时返回 0。
     */
    private static int invokeInt(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            return result instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(result));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 构建 additional_methods 模式的 raw_result Map（键值对，复现 executeAdditionalMethods 的返回结构）。
     */
    private static Map<String, Object> buildRawMap(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
