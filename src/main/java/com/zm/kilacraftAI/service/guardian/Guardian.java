package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.service.guardian.monitor.Monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 单个玩家的守护协调器：持有一束 {@link Monitor} + 共享冷却/静音/画像相关性。
 *
 * <p>跨 monitor 的防刷屏协调委托给 {@link GuardianCooldownHub}。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class Guardian {

    private final UUID playerId;
    private final List<Monitor> monitors;
    private final GuardianCooldownHub hub;

    public Guardian(UUID playerId, Collection<Monitor> monitors) {
        this(playerId, monitors, new GuardianCooldownHub());
    }

    public Guardian(UUID playerId, Collection<Monitor> monitors, GuardianCooldownHub hub) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.monitors = Collections.unmodifiableList(new ArrayList<>(monitors));
        this.hub = Objects.requireNonNull(hub, "hub");
    }

    public UUID playerId() {
        return playerId;
    }

    /** 该玩家的全部监听单元（不可变）。 */
    public List<Monitor> monitors() {
        return monitors;
    }

    /** 跨 monitor 防刷屏协调层（静音/分类冷却/优先级抢占/画像相关性）。 */
    public GuardianCooldownHub hub() {
        return hub;
    }
}
