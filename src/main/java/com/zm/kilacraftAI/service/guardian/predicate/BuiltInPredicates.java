package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.predicate.primitives.AirSupplyPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.FurnaceCookCompletePredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.FurnaceReadyCountPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.HealthRatioPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.InventoryCountPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.InWaterPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.NearbyEntityOutOfViewPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.NearbyEntityPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.OnFirePredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.WeatherPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.WorldTimeRangePredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.XpLevelPredicate;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 内置谓词原语注册入口：把首批原语登记到 {@link PredicateRegistry}，含语义说明 + 参数规格 + 工厂。
 *
 * <p>描述符聚合后喂给「建-monitor」LLM 提示词（§4.7：注册即 LLM 可发现）。
 * 工厂接受配置参数 Map，由 Step 3 的 JSON 反序列化器调用；本期保留工厂结构以便接入。
 * 新增原语时同步在此登记，并补对应单测。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class BuiltInPredicates {

    private BuiltInPredicates() {
    }

    @SuppressWarnings("unchecked")
    public static void registerDefaults(PredicateRegistry registry) {
        registry.register(new PredicateRegistry.Descriptor(
                "health_ratio",
                "玩家血量百分比（0.0~1.0）",
                List.of(
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值，0~1 之间", true)
                ),
                params -> new HealthRatioPredicate(
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").doubleValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "inventory_count",
                "背包指定材质物品的聚合数量",
                List.of(
                        param("material", "material", "物材质名", true),
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值", true)
                ),
                params -> new InventoryCountPredicate(
                        Material.valueOf(string(params, "material")),
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "nearby_entity_count",
                "指定半径内某类型实体数量（任意方向，用于刷怪塔混进稀有怪等注意力场景）",
                List.of(
                        param("entity_type", "entityType", "实体类型名", true),
                        param("radius", "number", "半径（格），不超过扫描半径", true),
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值", true)
                ),
                params -> new NearbyEntityPredicate(
                        EntityType.valueOf(string(params, "entity_type")),
                        number(params, "radius").doubleValue(),
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "nearby_entity_out_of_view",
                "指定半径内某类型实体中处于玩家视野外（侧方及背后）的数量；用于玩家看不见的威胁",
                List.of(
                        param("entity_type", "entityType", "实体类型名", true),
                        param("radius", "number", "半径（格），不超过扫描半径", true),
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值", true)
                ),
                params -> new NearbyEntityOutOfViewPredicate(
                        EntityType.valueOf(string(params, "entity_type")),
                        number(params, "radius").doubleValue(),
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "xp_level",
                "玩家经验等级",
                List.of(
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值", true)
                ),
                params -> new XpLevelPredicate(
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "world_time_range",
                "世界时间（0~23999）是否落在区间内；start>end 表示跨午夜",
                List.of(
                        param("start", "number", "区间起（ticks）", true),
                        param("end", "number", "区间止（ticks）", true)
                ),
                params -> new WorldTimeRangePredicate(
                        number(params, "start").longValue(),
                        number(params, "end").longValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "weather_is",
                "当前天气是否匹配（CLEAR/RAIN/THUNDER）",
                List.of(param("type", "string", "天气类型", true)),
                params -> new WeatherPredicate(
                        WeatherCondition.valueOf(string(params, "type")))
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "on_fire", "玩家是否着火", List.of(),
                params -> new OnFirePredicate()));

        registry.register(new PredicateRegistry.Descriptor(
                "in_water", "玩家是否在水中", List.of(),
                params -> new InWaterPredicate()));

        registry.register(new PredicateRegistry.Descriptor(
                "air_supply",
                "玩家剩余氧气（ticks）",
                List.of(
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值（ticks）", true)
                ),
                params -> new AirSupplyPredicate(
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "furnace_cook_complete",
                "指定位置熔炉是否烧好成品（产出槽非空）",
                List.of(
                        param("world", "world", "世界名（大小写敏感，不转大写）", true),
                        param("x", "number", "X 坐标", true),
                        param("y", "number", "Y 坐标", true),
                        param("z", "number", "Z 坐标", true)
                ),
                params -> new FurnaceCookCompletePredicate(
                        BlockPos.of(
                                rawString(params, "world"),
                                number(params, "x").intValue(),
                                number(params, "y").intValue(),
                                number(params, "z").intValue()))
        ));

        registry.register(new PredicateRegistry.Descriptor(
                "furnace_ready_count",
                "指定的一组熔炉中烧好成品（产出槽非空）的数量；用于熔炉房批量守护",
                List.of(
                        param("positions", "blockPosList", "熔炉位置列表，每项 {world,x,y,z}", true),
                        param("comparison", "comparison", "比较运算符", true),
                        param("threshold", "number", "阈值", true)
                ),
                params -> new FurnaceReadyCountPredicate(
                        parseBlockPosList(params.get("positions")),
                        Comparison.valueOf(string(params, "comparison")),
                        number(params, "threshold").intValue())
        ));
    }

    private static PredicateRegistry.ParamSpec param(String name, String type, String description, boolean required) {
        return new PredicateRegistry.ParamSpec(name, type, description, required);
    }

    private static String string(Map<String, ?> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        return v.toString().toUpperCase(Locale.ROOT);
    }

    /** 世界名等大小写敏感的原始字符串（不转大写，区别于枚举名匹配）。 */
    private static String rawString(Map<String, ?> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        return v.toString();
    }

    private static Number number(Map<String, ?> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        if (v instanceof Number n) {
            return n;
        }
        return Double.parseDouble(v.toString());
    }

    /** 解析熔炉位置列表 [{world,x,y,z}, ...]，世界名保持原样不转大写。任一字段缺失即抛出，避免静默构造非法 BlockPos。 */
    private static Set<BlockPos> parseBlockPosList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("缺少参数: positions");
        }
        Set<BlockPos> out = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Object world = m.get("world");
            if (world == null) {
                throw new IllegalArgumentException("熔炉位置缺少 world 字段");
            }
            out.add(BlockPos.of(
                    world.toString(),
                    toInt(m.get("x")),
                    toInt(m.get("y")),
                    toInt(m.get("z"))));
        }
        return out;
    }

    private static int toInt(Object v) {
        if (v == null) {
            throw new IllegalArgumentException("坐标字段缺失");
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
