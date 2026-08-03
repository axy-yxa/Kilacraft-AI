package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 玩家基础信息查询 Skill（player.info 域，18 个 action）
 *
 * <p>承载原 {@code GenericBukkitAPISkill} 中权限 {@code kilacraft.api.player.info} 下的全部 API：
 * 位置/视线位置/速度/游戏模式/飞行状态与速度/行走速度/主手偏好/Ping/载具/瞄准方块/指南针目标/
 * 上次死亡点/姿势/脚下方块/客户端语言/显示名/床重生点。
 * 入参/反参/字段名/线程模型/Folia 兼容逐项沿用原实现，零行为回归。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public class BukkitPlayerInfoSkill extends AbstractBukkitQuerySkill {

    private static final String SKILL_NAME = "bukkit_player_info";
    private static final String LOG_PREFIX = "Bukkit信息查询";

    private static final Set<String> PROBEABLE_ACTIONS = Set.of(
            "get_player_location", "get_player_eye_location", "get_player_velocity",
            "get_player_gamemode", "get_player_fly_status", "get_player_fly_speed",
            "get_player_walk_speed", "get_player_main_hand", "get_player_ping",
            "get_player_vehicle", "get_player_target_block", "get_player_compass_target",
            "get_player_last_death", "get_player_pose", "get_player_feet_block",
            "get_player_locale", "get_player_display_name", "get_player_bed_spawn");

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
        return PluginPermissionEnum.API_PLAYER_INFO.getNode();
    }

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    @Override
    protected SkillResult executeActions(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case "get_player_location" -> getLocation(player);
            case "get_player_eye_location" -> getEyeLocation(player);
            case "get_player_velocity" -> getVelocity(player);
            case "get_player_gamemode" -> getGamemode(player);
            case "get_player_fly_status" -> getFlyStatus(player);
            case "get_player_fly_speed" -> getFlySpeed(player);
            case "get_player_walk_speed" -> getWalkSpeed(player);
            case "get_player_main_hand" -> getMainHand(player);
            case "get_player_ping" -> getPing(player);
            case "get_player_vehicle" -> getVehicle(player);
            case "get_player_target_block" -> getTargetBlock(player);
            case "get_player_compass_target" -> getCompassTarget(player);
            case "get_player_last_death" -> getLastDeath(player);
            case "get_player_pose" -> getPose(player);
            case "get_player_feet_block" -> getFeetBlock(player);
            case "get_player_locale" -> getLocale(player);
            case "get_player_display_name" -> getDisplayName(player);
            case "get_player_bed_spawn" -> getBedSpawn(player);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 位置（additional_methods: getLocation.getX/getY/getZ/getWorld.getName，模板，%.2f）
     */
    private SkillResult getLocation(Player player) {
        Location loc = player.getLocation();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : null;
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("x", x);
        rawMap.put("y", y);
        rawMap.put("z", z);
        if (world != null) rawMap.put("world", world);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_player_location");
        dataMap.put("x", x);
        dataMap.put("y", y);
        dataMap.put("z", z);
        if (world != null) dataMap.put("world", world);
        // 模板 "位置：X={x}, Y={y}, Z={z}, 世界={world}"，x/y/z 经 formatMapValue（%.2f）
        String worldDisplay = world != null ? world : BukkitAPIResultFormatter.formatMapValue(null);
        String message = I18nService.tr("位置：X={}, Y={}, Z={}, 世界={}", String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z), worldDisplay);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 视线位置（method_chain: getEyeLocation，返回 Location）
     *
     * <p>Folia 路径：extractThreadSafeData(Location) 提取为 Map，formatLocationFromMap 格式化（「位置：...」）；
     * Spigot 路径：返回原始 Location，extractDataFromResult 提取字段，formatResult 走默认 toString（沿用原行为）。</p>
     */
    private SkillResult getEyeLocation(Player player) {
        Location loc = player.getEyeLocation();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_eye_location");
        String message;
        if (FoliaCompat.isFolia()) {
            Map<String, Object> locMap = new HashMap<>();
            BukkitAPIResultFormatter.putLocationFields(loc, locMap);
            dataMap.put("raw_result", locMap);
            for (Map.Entry<String, Object> entry : locMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatLocationFromMap(locMap);
        } else {
            // Spigot：原始 Location，formatResult 无 Location 分支，走 toString（原行为）
            dataMap.put("raw_result", loc);
            BukkitAPIResultFormatter.putLocationFields(loc, dataMap);
            message = loc.toString();
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 速度向量（method_chain: getVelocity，返回 Vector）
     *
     * <p>Folia 路径：extractThreadSafeData(Vector) 提取为 Map，formatVectorFromMap 格式化；
     * Spigot 路径：返回原始 Vector，formatResult 走默认 toString。</p>
     */
    private SkillResult getVelocity(Player player) {
        Vector velocity = player.getVelocity();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_velocity");
        String message;
        if (FoliaCompat.isFolia()) {
            Map<String, Object> vecMap = new HashMap<>();
            BukkitAPIResultFormatter.putVectorFields(velocity, vecMap);
            dataMap.put("raw_result", vecMap);
            for (Map.Entry<String, Object> entry : vecMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatVectorFromMap(vecMap);
        } else {
            dataMap.put("raw_result", velocity);
            BukkitAPIResultFormatter.putVectorFields(velocity, dataMap);
            message = velocity.toString();
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 游戏模式（method_chain: getGameMode，data_field: game_mode）
     */
    private SkillResult getGamemode(Player player) {
        GameMode gameMode = player.getGameMode();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", gameMode);
        dataMap.put("api_id", "get_player_gamemode");
        dataMap.put("game_mode", gameMode.name());
        String message = BukkitAPIResultFormatter.formatGameMode(gameMode);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 飞行状态（additional_methods: getAllowFlight/isFlying，模板「允许飞行：{allow_flight}, 正在飞行：{is_flying}」）
     */
    private SkillResult getFlyStatus(Player player) {
        boolean allowFlight = player.getAllowFlight();
        boolean isFlying = player.isFlying();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("allow_flight", allowFlight);
        rawMap.put("is_flying", isFlying);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_player_fly_status");
        dataMap.put("allow_flight", allowFlight);
        dataMap.put("is_flying", isFlying);
        String message = I18nService.tr("允许飞行：{}, 正在飞行：{}", BukkitAPIResultFormatter.formatBoolean(allowFlight), BukkitAPIResultFormatter.formatBoolean(isFlying));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 飞行速度（method_chain: getFlySpeed，data_field: fly_speed，Java 硬编码「速度：{:.2f}」）
     */
    private SkillResult getFlySpeed(Player player) {
        float speed = player.getFlySpeed();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", speed);
        dataMap.put("api_id", "get_player_fly_speed");
        dataMap.put("fly_speed", speed);
        // 原 formatResult：Float 且 apiId 含 "speed" → "速度：{:.2f}"
        String message = I18nService.tr("速度：{}", String.format("%.2f", speed));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 行走速度（method_chain: getWalkSpeed，data_field: walk_speed，Java 硬编码「速度：{:.2f}」）
     */
    private SkillResult getWalkSpeed(Player player) {
        float speed = player.getWalkSpeed();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", speed);
        dataMap.put("api_id", "get_player_walk_speed");
        dataMap.put("walk_speed", speed);
        String message = I18nService.tr("速度：{}", String.format("%.2f", speed));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 主手偏好（method_chain: getMainHand，data_field: main_hand）
     */
    private SkillResult getMainHand(Player player) {
        MainHand mainHand = player.getMainHand();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", mainHand);
        dataMap.put("api_id", "get_player_main_hand");
        dataMap.put("main_hand", mainHand.name());
        String message = BukkitAPIResultFormatter.formatMainHand(mainHand);
        return SkillResult.success(message, dataMap);
    }

    /**
     * Ping 延迟（method_chain: getPing，data_field: ping，Java 硬编码「延迟：{ping}ms ({quality})」）
     */
    private SkillResult getPing(Player player) {
        int ping = player.getPing();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", ping);
        dataMap.put("api_id", "get_player_ping");
        dataMap.put("ping", ping);
        String quality = ping < 100 ? (I18nService.isZh() ? "极好" : "Excellent") : (ping < 200 ? (I18nService.isZh() ? "良好" : "Good") : (ping < 300 ? (I18nService.isZh() ? "一般" : "Fair") : (I18nService.isZh() ? "较差" : "Poor")));
        String message = I18nService.tr("延迟：{}ms ({})", ping, quality);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 载具状态（additional_methods: isInsideVehicle，模板「是否在载具中：{in_vehicle}」）
     */
    private SkillResult getVehicle(Player player) {
        boolean inVehicle = player.isInsideVehicle();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("in_vehicle", inVehicle);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_player_vehicle");
        dataMap.put("in_vehicle", inVehicle);
        String message = I18nService.tr("是否在载具中：{}", BukkitAPIResultFormatter.formatBoolean(inVehicle));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 瞄准方块（method_chain: getTargetBlock，MAIN_THREAD，callSyncOnEntity，参数 {null,100}）
     *
     * <p>Folia 路径在区域线程内提取 Block 为 Map；Spigot 路径返回原始 Block 后由 toBlockMap 转为 Map。
     * 两条路径字段集与格式化文案统一（block_type/x/y/z/world → formatBlockFromMap）。</p>
     */
    private SkillResult getTargetBlock(Player player) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_target_block");
        String message;
        if (FoliaCompat.isFolia()) {
            Map<String, Object> blockMap = FoliaCompat.callSyncOnEntity(player, () -> {
                Block b = player.getTargetBlock(null, 100);
                if (b == null) {
                    return null;
                }
                Map<String, Object> map = new HashMap<>();
                BukkitAPIResultFormatter.putBlockFields(b, map);
                return map;
            }, 5);
            if (blockMap == null || blockMap.isEmpty()) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            dataMap.put("raw_result", blockMap);
            for (Map.Entry<String, Object> entry : blockMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatBlockFromMap(blockMap);
        } else {
            Block block = FoliaCompat.callSyncOnEntity(player, () -> player.getTargetBlock(null, 100), 5);
            if (block == null) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            dataMap.put("raw_result", block);
            message = BukkitAPIResultFormatter.formatBlockFromMap(toBlockMap(block));
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 指南针目标（method_chain: getCompassTarget，返回 Location）
     *
     * <p>Folia 路径：extractThreadSafeData(Location)→Map，formatLocationFromMap；
     * Spigot 路径：返回原始 Location，extractDataFromResult 提取字段，formatResult 走 toString。</p>
     */
    private SkillResult getCompassTarget(Player player) {
        Location loc = player.getCompassTarget();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_compass_target");
        String message;
        if (FoliaCompat.isFolia()) {
            if (loc == null) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            Map<String, Object> locMap = new HashMap<>();
            BukkitAPIResultFormatter.putLocationFields(loc, locMap);
            dataMap.put("raw_result", locMap);
            for (Map.Entry<String, Object> entry : locMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatLocationFromMap(locMap);
        } else {
            dataMap.put("raw_result", loc);
            if (loc != null) {
                BukkitAPIResultFormatter.putLocationFields(loc, dataMap);
                message = loc.toString();
            } else {
                message = I18nService.tr("无结果");
            }
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 上次死亡位置（method_chain: getLastDeathLocation，返回 Location 或 null）
     *
     * <p>1.18+ API，编译期通过反射；Folia/Spigot 双路径同 compass_target。</p>
     */
    private SkillResult getLastDeath(Player player) {
        Location loc = invokeLocation(player, "getLastDeathLocation");
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_last_death");
        String message;
        if (FoliaCompat.isFolia()) {
            if (loc == null) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            Map<String, Object> locMap = new HashMap<>();
            BukkitAPIResultFormatter.putLocationFields(loc, locMap);
            dataMap.put("raw_result", locMap);
            for (Map.Entry<String, Object> entry : locMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatLocationFromMap(locMap);
        } else {
            dataMap.put("raw_result", loc);
            if (loc != null) {
                BukkitAPIResultFormatter.putLocationFields(loc, dataMap);
                message = loc.toString();
            } else {
                message = I18nService.tr("无结果");
            }
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 姿势（method_chain: getPose，data_field: pose）
     */
    private SkillResult getPose(Player player) {
        org.bukkit.entity.Pose pose = player.getPose();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", pose);
        dataMap.put("api_id", "get_player_pose");
        dataMap.put("pose", pose.name());
        String message = BukkitAPIResultFormatter.formatPose(pose);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 脚下方块（getFeetBlock 特殊路径：getLocation().subtract(0,1,0).getBlock()，callSyncOnEntity）
     *
     * <p>Folia 路径：区域线程内 extractThreadSafeData(Block)→Map，formatFeetBlockFromMap；
     * Spigot 路径：返回原始 Block，extractDataFromResult 提取 block_type/x/y/z/world，formatResult 硬编码文案。</p>
     */
    private SkillResult getFeetBlock(Player player) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_feet_block");
        String message;
        if (FoliaCompat.isFolia()) {
            Map<String, Object> blockMap = FoliaCompat.callSyncOnEntity(player, () -> {
                Location feetLoc = player.getLocation().subtract(0, 1, 0);
                Block block = feetLoc.getBlock();
                Map<String, Object> map = new HashMap<>();
                BukkitAPIResultFormatter.putBlockFields(block, map);
                return map;
            }, 5);
            dataMap.put("raw_result", blockMap);
            if (blockMap != null) {
                for (Map.Entry<String, Object> entry : blockMap.entrySet()) {
                    dataMap.put(entry.getKey(), entry.getValue());
                }
            }
            message = BukkitAPIResultFormatter.formatFeetBlockFromMap(blockMap);
        } else {
            Block block = FoliaCompat.callSyncOnEntity(player, () -> {
                Location feetLoc = player.getLocation().subtract(0, 1, 0);
                return feetLoc.getBlock();
            }, 5);
            dataMap.put("raw_result", block);
            if (block != null) {
                BukkitAPIResultFormatter.putBlockFields(block, dataMap);
                String chineseName = ItemTranslator.getInstance().translateToChinese(block.getType().name());
                message = I18nService.tr("脚下方块：{}（位置：X={}, Y={}, Z={}）", chineseName, block.getX(), block.getY(), block.getZ());
            } else {
                message = I18nService.tr("无结果");
            }
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 客户端语言（method_chain: getLocale，data_field: locale，字符串无前缀）
     */
    private SkillResult getLocale(Player player) {
        String locale = player.getLocale();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", locale);
        dataMap.put("api_id", "get_player_locale");
        dataMap.put("locale", locale);
        // 原 formatResult：String 非 biome → 走默认 toString
        String message = String.valueOf(locale);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 显示名称（method_chain: getDisplayName，data_field: display_name，字符串无前缀）
     */
    private SkillResult getDisplayName(Player player) {
        String displayName = player.getDisplayName();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", displayName);
        dataMap.put("api_id", "get_player_display_name");
        dataMap.put("display_name", displayName);
        String message = String.valueOf(displayName);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 床重生点（method_chain: getBedSpawnLocation，返回 Location 或 null）
     *
     * <p>Folia 路径：extractThreadSafeData(Location)→Map；Spigot 路径：返回原始 Location，
     * extractDataFromResult 提取字段，formatResult 走 toString（null→「无结果」）。</p>
     */
    private SkillResult getBedSpawn(Player player) {
        Location loc = player.getBedSpawnLocation();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_player_bed_spawn");
        String message;
        if (FoliaCompat.isFolia()) {
            if (loc == null) {
                dataMap.put("raw_result", null);
                return SkillResult.success(I18nService.tr("无结果"), dataMap);
            }
            Map<String, Object> locMap = new HashMap<>();
            BukkitAPIResultFormatter.putLocationFields(loc, locMap);
            dataMap.put("raw_result", locMap);
            for (Map.Entry<String, Object> entry : locMap.entrySet()) {
                dataMap.put(entry.getKey(), entry.getValue());
            }
            message = BukkitAPIResultFormatter.formatLocationFromMap(locMap);
        } else {
            dataMap.put("raw_result", loc);
            if (loc != null) {
                BukkitAPIResultFormatter.putLocationFields(loc, dataMap);
                message = loc.toString();
            } else {
                message = I18nService.tr("无结果");
            }
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 把 Block 转为字段 Map（复现 formatBlockFromMap 的输入构造，用于 Spigot 路径的瞄准方块格式化）。
     */
    private static Map<String, Object> toBlockMap(Block block) {
        Map<String, Object> map = new HashMap<>();
        BukkitAPIResultFormatter.putBlockFields(block, map);
        return map;
    }

    /**
     * 反射调用返回 Location 的无参方法（用于 1.18+ API 如 getLastDeathLocation）。
     */
    private static Location invokeLocation(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            return result instanceof Location l ? l : null;
        } catch (Exception e) {
            return null;
        }
    }
}
