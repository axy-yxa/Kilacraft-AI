package com.zm.kilacraftAI.service.guardian.predicate;

import org.bukkit.World;

import java.util.Objects;

/**
 * 守护谓词引用的方块坐标（如熔炉位置）。不可变值对象，用作快照读取请求键与结果 Map 键。
 * worldName + 整数坐标唯一确定一个方块。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record BlockPos(String worldName, int x, int y, int z) {

    public BlockPos {
        Objects.requireNonNull(worldName, "worldName");
    }

    /** 从世界名 + 整数坐标构造。 */
    public static BlockPos of(String worldName, int x, int y, int z) {
        return new BlockPos(worldName, x, y, z);
    }

    /** 从 Bukkit World + 整数坐标构造。 */
    public static BlockPos of(World world, int x, int y, int z) {
        return new BlockPos(world.getName(), x, y, z);
    }
}
