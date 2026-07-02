package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.BlockPos;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

import java.util.Set;

/**
 * 熔炉批量烧好计数原语：给定一组熔炉位置，统计产出槽非空（{@code resultReady}）的数量，
 * 按比较符与阈值求值。
 *
 * <p>价值定位：远端熔炉房是玩家<strong>非即时感知</strong>的典型——100 个炉子哪个烧好了，
 * 玩家不在旁边根本不知道。用一个 monitor 守一组炉子（{@code ready_count >= 1}），
 * 而非为每个炉子建独立 monitor。位置集合由守护配置提供，
 * {@link com.zm.kilacraftAI.service.guardian.predicate.PlayerStateService} 按请求拉取式读取。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class FurnaceReadyCountPredicate extends ValuePredicate {

    private final Set<BlockPos> positions;
    private final Comparison comparison;
    private final int threshold;

    public FurnaceReadyCountPredicate(Set<BlockPos> positions, Comparison comparison, int threshold) {
        this.positions = Set.copyOf(positions);
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int count = 0;
        for (BlockPos pos : positions) {
            PlayerState.FurnaceRead read = state.furnaceAt(pos);
            if (read != null && read.resultReady()) {
                count++;
            }
        }
        recordValue(count);
        return comparison.test(count, threshold);
    }

    public Set<BlockPos> positions() {
        return positions;
    }

    public Comparison comparison() {
        return comparison;
    }

    public int threshold() {
        return threshold;
    }

    @Override
    public Set<BlockPos> requestedFurnacePositions() {
        return positions;
    }
}
