package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.BlockPos;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;

import java.util.Set;

/**
 * 熔炉烧好原语：指定位置的熔炉产出槽有成品（{@code resultReady=true}）时触发。
 *
 * <p>典型：配合 {@code WHILE_TRUE} 做熔炉循环守护（取出成品 → 放入原料）。
 * 位置由守护配置提供；{@link PlayerStateService#snapshot} 按 monitor 声明的位置拉取式读取。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class FurnaceCookCompletePredicate implements Predicate {

    private final BlockPos position;

    public FurnaceCookCompletePredicate(BlockPos position) {
        this.position = position;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        PlayerState.FurnaceRead read = state.furnaceAt(position);
        return read != null && read.resultReady();
    }

    public BlockPos position() {
        return position;
    }

    @Override
    public Set<BlockPos> requestedFurnacePositions() {
        return Set.of(position);
    }
}
