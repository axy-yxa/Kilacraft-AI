package com.zm.kilacraftAI.service.suggestion;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家级推荐开关管理器。
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public final class SuggestionManager {

    /**
     * 玩家 → 是否关闭推荐。不存在 = 默认开启。
     */
    private final ConcurrentHashMap<UUID, Boolean> disabled = new ConcurrentHashMap<>();

    /**
     * 玩家是否开启了推荐（默认 true）。
     */
    public boolean isSuggestionEnabled(UUID playerId) {
        return !disabled.getOrDefault(playerId, false);
    }

    /**
     * 关闭推荐（/kila suggestion off）。
     */
    public void disable(UUID playerId) {
        disabled.put(playerId, true);
    }

    /**
     * 开启推荐（/kila suggestion on）。
     */
    public void enable(UUID playerId) {
        disabled.remove(playerId);
    }

    /**
     * 玩家下线时清理（内存态不持久化，重启自然清空）。
     */
    public void clearPlayer(UUID playerId) {
        disabled.remove(playerId);
    }
}
