package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;
import org.bukkit.Material;

/**
 * 背包某物聚合数量原语：跨背包所有格聚合，按比较符与阈值求值。
 *
 * <p>典型：铁锭 &gt;= 64（凑够目标）、火把 &lt; 16（资源提醒）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class InventoryCountPredicate extends ValuePredicate {

    private final Material material;
    private final Comparison comparison;
    private final int threshold;

    public InventoryCountPredicate(Material material, Comparison comparison, int threshold) {
        this.material = material;
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int count = state.inventoryCount(material);
        recordValue(count);
        return comparison.test(count, threshold);
    }

    public Material material() {
        return material;
    }

    public Comparison comparison() {
        return comparison;
    }

    public int threshold() {
        return threshold;
    }
}
