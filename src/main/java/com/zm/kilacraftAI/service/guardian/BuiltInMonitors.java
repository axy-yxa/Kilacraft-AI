package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.config.GuardianConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.action.GuardianLlmAction;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.InventoryFreeSlotsPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.InventoryOpenPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.LowDurabilityPredicate;
import com.zm.kilacraftAI.service.guardian.predicate.primitives.ThreatOutOfViewAndNearPredicate;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.List;

/**
 * 内置 monitor 工厂：Java 硬编码创建守护场景，{@code /kila guardian on} 自动生效。
 *
 * <p>价值边界：只对玩家非即时感知、且预见窗口显著大于 LLM 端到端延迟的场景发声——
 * 窗口短于延迟（苦力怕点燃/投射物）、有游戏音效（破门）、或事后可见（稀有生物生成/已受攻击）的均不内置。</p>
 *
 * <p>两类来源：{@link #createEventMonitors}（事件型，声明事件类型 + 过滤函数，可选带 triggerPredicate）、
 * {@link #createDefaultPollingMonitors}（轮询型，按 cadence 调度，triggerPredicate 做边沿触发避免刷屏）。
 * id 以 {@code _} 前缀标识内置，玩家无法通过自然语言取消。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-11
 */
public final class BuiltInMonitors {

    private static final long THREAT_COOLDOWN_MS = 30_000L;
    private static final long INVENTORY_NEAR_FULL_COOLDOWN_MS = 60_000L;
    private static final long LOW_DURABILITY_COOLDOWN_MS = 60_000L;

    private static final int INVENTORY_FREE_SLOTS_THRESHOLD = 6;
    private static final long INVENTORY_NEAR_FULL_CADENCE_TICKS = 600L;
    private static final double LOW_DURABILITY_THRESHOLD = 0.05;
    private static final long LOW_DURABILITY_CADENCE_TICKS = 600L;
    /**
     * 视野外威胁告警最大距离：玩家正面对的威胁自己能看见，超出此值的锁定短期无威胁。覆盖近战 + 苦力怕 + 蜘蛛跳跃。
     */
    private static final double THREAT_MAX_DISTANCE = 8.0;

    private BuiltInMonitors() {
    }

    /**
     * 内置事件型 monitor（预见窗口 > LLM 端到端延迟的场景）。
     *
     * @param configManager 守护配置管理器（提供 LLM 系统提示词）
     * @return 不可变 monitor 列表
     */
    public static List<Monitor> createEventMonitors(GuardianConfigManager configManager) {
        return List.of(buildThreatTargetMonitor(configManager));
    }

    /**
     * 内置轮询型 monitor（{@code /kila guardian on} 自动启用）。阈值/周期为产品决策，硬编码不开放配置。
     *
     * @param configManager 守护配置管理器（提供 LLM 系统提示词）
     * @return 不可变 monitor 列表
     */
    public static List<Monitor> createDefaultPollingMonitors(GuardianConfigManager configManager) {
        return List.of(buildInventoryNearFullMonitor(configManager), buildLowDurabilityMonitor(configManager));
    }

    /**
     * 背包剩余空格 ≤ 阈值时提醒整理。玩家打开任何物品界面（含自身背包）时跳过——他正在查看物品状态。
     * triggerPredicate 做边沿触发（假→真才提醒），cooldown 抑制重复。
     */
    static Monitor buildInventoryNearFullMonitor(GuardianConfigManager configManager) {
        Predicate trigger = Predicate.and(new InventoryFreeSlotsPredicate(Comparison.LESS_EQUAL, INVENTORY_FREE_SLOTS_THRESHOLD), Predicate.not(new InventoryOpenPredicate()));
        GuardianLlmAction action = new GuardianLlmAction(configManager, "玩家的背包快满了。请提醒玩家整理背包或回家存放物品。");
        return Monitor.polling("_inventory_near_full", action, INVENTORY_NEAR_FULL_CADENCE_TICKS).displayName(I18nService.tr("背包空间")).trigger(trigger).cooldownMillis(INVENTORY_NEAR_FULL_COOLDOWN_MS).build();
    }

    /**
     * 手持 + 盔甲中耐久最低项剩余 ≤ 阈值时提醒修复或更换。玩家打开任何物品界面时跳过——他能看到装备耐久。
     * triggerPredicate 做边沿触发（假→真才提醒），cooldown 抑制重复。
     */
    static Monitor buildLowDurabilityMonitor(GuardianConfigManager configManager) {
        Predicate trigger = Predicate.and(new LowDurabilityPredicate(LOW_DURABILITY_THRESHOLD), Predicate.not(new InventoryOpenPredicate()));
        GuardianLlmAction action = new GuardianLlmAction(configManager, "玩家的装备或工具耐久即将耗尽。请提醒玩家尽快修复或更换，避免在关键时刻损坏。");
        return Monitor.polling("_low_durability", action, LOW_DURABILITY_CADENCE_TICKS).displayName(I18nService.tr("装备耐久")).trigger(trigger).cooldownMillis(LOW_DURABILITY_COOLDOWN_MS).build();
    }

    /**
     * 怪物锁定玩家，在到达/攻击前预警。双重过滤：事件层排除非威胁锁定原因（RANDOM_TARGET/FORGET 等），
     * 触发谓词限定视野外且近距——玩家正面对的威胁自己能看见，只有侧方/背后且能构成威胁的锁定才提醒。
     */
    static Monitor buildThreatTargetMonitor(GuardianConfigManager configManager) {
        Predicate trigger = new ThreatOutOfViewAndNearPredicate(THREAT_MAX_DISTANCE);
        GuardianLlmAction action = new GuardianLlmAction(configManager, "一个敌对生物发现了玩家，正在锁定/追踪。请简短提醒玩家注意身后的威胁，语气紧迫但不要慌张。");
        return Monitor.event("_threat_target", action, EntityTargetEvent.class, (EntityTargetEvent event) -> {
            if (!(event.getTarget() instanceof Player)) {
                return false;
            }
            if (!HostileEntityChecker.isHostile(event.getEntity().getType())) {
                return false;
            }
            EntityTargetEvent.TargetReason reason = event.getReason();
            return reason != EntityTargetEvent.TargetReason.TARGET_DIED && reason != EntityTargetEvent.TargetReason.FORGOT_TARGET && reason != EntityTargetEvent.TargetReason.RANDOM_TARGET;
        }).displayName(I18nService.tr("威胁锁定")).trigger(trigger).cooldownMillis(THREAT_COOLDOWN_MS).build();
    }
}
