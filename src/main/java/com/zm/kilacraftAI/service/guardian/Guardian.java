package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.service.guardian.monitor.Monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单个玩家的守护协调器：持有一束 {@link Monitor}。
 *
 * <p>monitors 列表在启用时一次性装配（默认套餐），运行期不变。
 * 防刷屏由每个 Monitor 自身的 {@code cooldownMillis} 承担。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class Guardian {

    private final List<Monitor> monitors;

    public Guardian(Collection<Monitor> monitors) {
        this.monitors = new CopyOnWriteArrayList<>(new ArrayList<>(monitors));
    }

    /**
     * 该玩家的全部监听单元。
     */
    public List<Monitor> monitors() {
        return monitors;
    }
}
