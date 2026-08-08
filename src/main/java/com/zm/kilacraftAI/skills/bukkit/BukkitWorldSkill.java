package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.service.bukkit.BukkitAPIResultFormatter;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 世界信息查询 Skill（world 域，21 个 action）
 *
 * <p>承载原 {@code GenericBukkitAPISkill} 中权限 {@code kilacraft.api.world.info} 下的全部 API：
 * 时间/天气/世界信息/种子/出生点/边界/高度/生成规则/PVP/生物群系/温湿度/玩家与实体数量/
 * 海平面/天气持续时间/总时间/游戏时间/袭击。
 * 入参/反参/字段名/线程模型/Folia 兼容逐项沿用原实现，零行为回归。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public class BukkitWorldSkill extends AbstractBukkitQuerySkill {

    private static final String SKILL_NAME = "world_info";
    private static final String LOG_PREFIX = "Bukkit世界查询";

    private static final Set<String> PROBEABLE_ACTIONS = Set.of(
            "get_world_time", "get_weather", "get_world_info", "get_world_seed", "get_world_spawn",
            "get_world_border", "get_world_height_limit", "get_world_spawn_rules", "get_world_pvp",
            "get_world_biome", "get_world_temperature", "get_world_humidity", "get_world_player_count",
            "get_world_living_entities", "get_world_entity_count", "get_world_sea_level",
            "get_world_clear_weather_duration", "get_world_thunder_duration", "get_world_full_time",
            "get_world_game_time", "get_world_raids");

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
        return PluginPermissionEnum.API_WORLD_INFO.getNode();
    }

    @Override
    public Set<String> getProbeableActions() {
        return PROBEABLE_ACTIONS;
    }

    @Override
    protected SkillResult executeActions(String action, Player player, Map<String, String> entities) {
        return switch (action) {
            case "get_world_time" -> getWorldTime(player);
            case "get_weather" -> getWeather(player);
            case "get_world_info" -> getWorldInfo(player);
            case "get_world_seed" -> getWorldSeed(player);
            case "get_world_spawn" -> getWorldSpawn(player);
            case "get_world_border" -> getWorldBorder(player);
            case "get_world_height_limit" -> getWorldHeightLimit(player);
            case "get_world_spawn_rules" -> getWorldSpawnRules(player);
            case "get_world_pvp" -> getWorldPvp(player);
            case "get_world_biome" -> getWorldBiome(player);
            case "get_world_temperature" -> getWorldTemperature(player);
            case "get_world_humidity" -> getWorldHumidity(player);
            case "get_world_player_count" -> getWorldPlayerCount(player);
            case "get_world_living_entities" -> getWorldLivingEntities(player);
            case "get_world_entity_count" -> getWorldEntityCount(player);
            case "get_world_sea_level" -> getWorldSeaLevel(player);
            case "get_world_clear_weather_duration" -> getWorldClearWeatherDuration(player);
            case "get_world_thunder_duration" -> getWorldThunderDuration(player);
            case "get_world_full_time" -> getWorldFullTime(player);
            case "get_world_game_time" -> getWorldGameTime(player);
            case "get_world_raids" -> getWorldRaids(player);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action));
        };
    }

    /**
     * 世界时间（method_chain: getTime，data_field: time_ticks，Java 硬编码 formatGameTime）
     */
    private SkillResult getWorldTime(Player player) {
        long time = player.getWorld().getTime();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", time);
        dataMap.put("api_id", "get_world_time");
        dataMap.put("time_ticks", time);
        String message = BukkitAPIResultFormatter.formatGameTime(time);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 天气（additional_methods: hasStorm/isThundering，模板被忽略，Java 硬编码 formatWeatherResult）
     */
    private SkillResult getWeather(Player player) {
        boolean hasStorm = player.getWorld().hasStorm();
        boolean isThundering = player.getWorld().isThundering();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("has_storm", hasStorm);
        rawMap.put("is_thundering", isThundering);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_weather");
        dataMap.put("has_storm", hasStorm);
        dataMap.put("is_thundering", isThundering);
        String message = BukkitAPIResultFormatter.formatWeatherResult(rawMap);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界基本信息（additional_methods: getName/getEnvironment/getDifficulty，模板）
     */
    private SkillResult getWorldInfo(Player player) {
        World world = player.getWorld();
        String name = world.getName();
        World.Environment env = world.getEnvironment();
        Difficulty difficulty = world.getDifficulty();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("name", name);
        rawMap.put("environment", env);
        rawMap.put("difficulty", difficulty);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_info");
        dataMap.put("name", name);
        dataMap.put("environment", env.name());
        dataMap.put("difficulty", difficulty.name());
        // 模板 "世界：{name}, 类型：{environment}, 难度：{difficulty}"，env/difficulty 经 formatMapValue 翻译
        String message = I18nService.tr("世界：{}, 类型：{}, 难度：{}", name, BukkitAPIResultFormatter.formatEnvironment(env), BukkitAPIResultFormatter.formatDifficulty(difficulty));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界种子（method_chain: getSeed，data_field: seed）
     */
    private SkillResult getWorldSeed(Player player) {
        long seed = player.getWorld().getSeed();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", seed);
        dataMap.put("api_id", "get_world_seed");
        dataMap.put("seed", seed);
        String message = I18nService.tr("世界种子：{}", seed);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界出生点（method_chain: getSpawnLocation，返回 Location）
     */
    private SkillResult getWorldSpawn(Player player) {
        Location loc = player.getWorld().getSpawnLocation();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_world_spawn");
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
            dataMap.put("raw_result", loc);
            BukkitAPIResultFormatter.putLocationFields(loc, dataMap);
            message = loc.toString();
        }
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界边界（additional_methods: getWorldBorder.getSize/getCenter.getX/getCenter.getZ，模板）
     */
    private SkillResult getWorldBorder(Player player) {
        var border = player.getWorld().getWorldBorder();
        double size = border.getSize();
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("border_size", size);
        rawMap.put("center_x", centerX);
        rawMap.put("center_z", centerZ);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_border");
        dataMap.put("border_size", size);
        dataMap.put("center_x", centerX);
        dataMap.put("center_z", centerZ);
        // 模板 "世界边界：大小={border_size}格，中心：X={center_x}, Z={center_z}"，double 经 %.2f
        String message = I18nService.tr("世界边界：大小={}格，中心：X={}, Z={}", String.format("%.2f", size), String.format("%.2f", centerX), String.format("%.2f", centerZ));
        return SkillResult.success(message, dataMap);
    }

    /**
     * 高度限制（additional_methods: getMinHeight/getMaxHeight，模板）
     */
    private SkillResult getWorldHeightLimit(Player player) {
        int minHeight = player.getWorld().getMinHeight();
        int maxHeight = player.getWorld().getMaxHeight();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("min_height", minHeight);
        rawMap.put("max_height", maxHeight);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_height_limit");
        dataMap.put("min_height", minHeight);
        dataMap.put("max_height", maxHeight);
        String message = I18nService.tr("高度范围：{} ~ {}", minHeight, maxHeight);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 生物生成规则（additional_methods: getAllowMonsters/getAllowAnimals，模板）
     */
    private SkillResult getWorldSpawnRules(Player player) {
        boolean allowMonsters = player.getWorld().getAllowMonsters();
        boolean allowAnimals = player.getWorld().getAllowAnimals();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("allow_monsters", allowMonsters);
        rawMap.put("allow_animals", allowAnimals);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_spawn_rules");
        dataMap.put("allow_monsters", allowMonsters);
        dataMap.put("allow_animals", allowAnimals);
        String message = I18nService.tr("允许怪物：{}, 允许动物：{}", BukkitAPIResultFormatter.formatBoolean(allowMonsters), BukkitAPIResultFormatter.formatBoolean(allowAnimals));
        return SkillResult.success(message, dataMap);
    }

    /**
     * PVP 设置（method_chain: getPVP，data_field: pvp，Java 硬编码 formatBooleanResult）
     */
    private SkillResult getWorldPvp(Player player) {
        boolean pvp = player.getWorld().getPVP();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", pvp);
        dataMap.put("api_id", "get_world_pvp");
        dataMap.put("pvp", pvp);
        // 原 formatBooleanResult：apiId 含 "pvp" → 特殊文案
        String message = BukkitAPIResultFormatter.formatBooleanResult("get_world_pvp", pvp, null);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 生物群系（method_chain: getBiome，需主线程，参数 {blockX, blockZ}，data_field: biome）
     *
     * <p>Folia 路径：区域线程内 getBiome→Biome.name() 提取为 String；Spigot 路径：返回 Biome 枚举。
     * 两者最终都经 formatBiomeByName 格式化。</p>
     */
    private SkillResult getWorldBiome(Player player) {
        World world = player.getWorld();
        int blockX = player.getLocation().getBlockX();
        int blockZ = player.getLocation().getBlockZ();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("api_id", "get_world_biome");
        String biomeName;
        if (FoliaCompat.isFolia()) {
            // 复现原 invokeOnMainThread 区域线程内 extractThreadSafeData(Biome)→name()
            biomeName = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> {
                Biome b = world.getBiome(blockX, blockZ);
                return b.name();
            }, 5);
            dataMap.put("raw_result", biomeName);
        } else {
            Biome biome = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> world.getBiome(blockX, blockZ), 5);
            dataMap.put("raw_result", biome);
            biomeName = biome.name();
        }
        dataMap.put("biome", biomeName);
        String message = BukkitAPIResultFormatter.formatBiomeByName(biomeName);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 温度（method_chain: getTemperature，需主线程，参数 {blockX, blockZ}，data_field: temperature，%.2f）
     */
    private SkillResult getWorldTemperature(Player player) {
        World world = player.getWorld();
        int blockX = player.getLocation().getBlockX();
        int blockZ = player.getLocation().getBlockZ();
        double temp = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> world.getTemperature(blockX, blockZ), 5);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", temp);
        dataMap.put("api_id", "get_world_temperature");
        dataMap.put("temperature", temp);
        // 原 formatResult：Double 且 biome/temperature/humidity → String.format("%.2f", temp)
        String message = String.format("%.2f", temp);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 湿度（method_chain: getHumidity，需主线程，参数 {blockX, blockZ}，data_field: humidity，%.2f）
     */
    private SkillResult getWorldHumidity(Player player) {
        World world = player.getWorld();
        int blockX = player.getLocation().getBlockX();
        int blockZ = player.getLocation().getBlockZ();
        double humidity = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> world.getHumidity(blockX, blockZ), 5);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", humidity);
        dataMap.put("api_id", "get_world_humidity");
        dataMap.put("humidity", humidity);
        String message = String.format("%.2f", humidity);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界玩家数量（additional_methods: getPlayers.size，IO 线程）
     */
    private SkillResult getWorldPlayerCount(Player player) {
        int count = player.getWorld().getPlayers().size();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("player_count", count);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_player_count");
        dataMap.put("player_count", count);
        String message = I18nService.tr("当前世界玩家数量：{}", count);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界生物数量（additional_methods: getLivingEntities.size，getLivingEntities 需主线程，callSync）
     */
    private SkillResult getWorldLivingEntities(Player player) {
        World world = player.getWorld();
        int count = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> world.getLivingEntities().size(), 5);
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("living_entities", count);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_living_entities");
        dataMap.put("living_entities", count);
        String message = I18nService.tr("当前世界生物数量：{}", count);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界实体总数（additional_methods: getEntities.size，getEntities 需主线程，callSync）
     */
    private SkillResult getWorldEntityCount(Player player) {
        World world = player.getWorld();
        int count = FoliaCompat.callSync(KilacraftAI.getInstance(), () -> world.getEntities().size(), 5);
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("entity_count", count);
        dataMap.put("raw_result", rawMap);
        dataMap.put("api_id", "get_world_entity_count");
        dataMap.put("entity_count", count);
        String message = I18nService.tr("当前世界实体总数：{}", count);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 海平面（method_chain: getSeaLevel，data_field: sea_level，数值无前缀）
     */
    private SkillResult getWorldSeaLevel(Player player) {
        int seaLevel = player.getWorld().getSeaLevel();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", seaLevel);
        dataMap.put("api_id", "get_world_sea_level");
        dataMap.put("sea_level", seaLevel);
        String message = String.valueOf(seaLevel);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 晴天剩余时间（method_chain: getClearWeatherDuration，data_field: clear_weather_duration，数值无前缀）
     */
    private SkillResult getWorldClearWeatherDuration(Player player) {
        int duration = player.getWorld().getClearWeatherDuration();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", duration);
        dataMap.put("api_id", "get_world_clear_weather_duration");
        dataMap.put("clear_weather_duration", duration);
        String message = String.valueOf(duration);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 雷暴剩余时间（method_chain: getThunderDuration，data_field: thunder_duration，数值无前缀）
     */
    private SkillResult getWorldThunderDuration(Player player) {
        int duration = player.getWorld().getThunderDuration();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", duration);
        dataMap.put("api_id", "get_world_thunder_duration");
        dataMap.put("thunder_duration", duration);
        String message = String.valueOf(duration);
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界总时间（method_chain: getFullTime，data_field: full_time，Java 硬编码 formatWorldTime）
     */
    private SkillResult getWorldFullTime(Player player) {
        long time = player.getWorld().getFullTime();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", time);
        dataMap.put("api_id", "get_world_full_time");
        dataMap.put("full_time", time);
        String message = BukkitAPIResultFormatter.formatWorldTime(time, "get_world_full_time");
        return SkillResult.success(message, dataMap);
    }

    /**
     * 世界游戏时间（method_chain: getGameTime，data_field: game_time，Java 硬编码 formatWorldTime）
     */
    private SkillResult getWorldGameTime(Player player) {
        long time = player.getWorld().getGameTime();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", time);
        dataMap.put("api_id", "get_world_game_time");
        dataMap.put("game_time", time);
        String message = BukkitAPIResultFormatter.formatWorldTime(time, "get_world_game_time");
        return SkillResult.success(message, dataMap);
    }

    /**
     * 袭击事件（method_chain: getRaids，data_field: raids，Collection→size 注入，Java 硬编码 formatRaids）
     */
    private SkillResult getWorldRaids(Player player) {
        Collection<?> raids = player.getWorld().getRaids();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("raw_result", raids);
        dataMap.put("api_id", "get_world_raids");
        // 原 data_field Collection 注入：raids = collection.size()
        dataMap.put("raids", raids.size());
        String message = BukkitAPIResultFormatter.formatRaids(raids);
        return SkillResult.success(message, dataMap);
    }
}
