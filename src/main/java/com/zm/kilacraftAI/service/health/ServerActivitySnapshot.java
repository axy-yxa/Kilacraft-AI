package com.zm.kilacraftAI.service.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务器活动快照（采样窗口内采集，用于对比前后变化）
 *
 * <p>采集时机：Profiler 采样前后各一次，通过差值反映采样窗口内的服务器活动。</p>
 * <p>不可变记录，所有字段均为不可变集合，线程安全。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public record ServerActivitySnapshot(
        /* 世界名 → 已加载区块数 */
        Map<String, Integer> worldChunkCounts,
        /* 世界名 → 在线玩家数 */
        Map<String, Integer> worldPlayerCounts,
        /* 玩家名 → "世界名 (区块x, 区块z)"，坐标精确到区块级 */
        Map<String, String> playerLocations,
        /* 玩家名 → [方块X, 方块Z]（方块级精确坐标，用于移动距离计算，不对外暴露） */
        Map<String, int[]> playerBlockCoords) {
    /**
     * 空快照常量（采集失败时使用）
     */
    public static final ServerActivitySnapshot EMPTY = new ServerActivitySnapshot(Map.of(), Map.of(), Map.of(), Map.of());

    /**
     * 构造时自动包装为不可变 Map，确保发布后不可变
     */
    public ServerActivitySnapshot {
        worldChunkCounts = Collections.unmodifiableMap(new LinkedHashMap<>(worldChunkCounts));
        worldPlayerCounts = Collections.unmodifiableMap(new LinkedHashMap<>(worldPlayerCounts));
        playerLocations = Collections.unmodifiableMap(new LinkedHashMap<>(playerLocations));
        playerBlockCoords = Collections.unmodifiableMap(new LinkedHashMap<>(playerBlockCoords));
    }
}
