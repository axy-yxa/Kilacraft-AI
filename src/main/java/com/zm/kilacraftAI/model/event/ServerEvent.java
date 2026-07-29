package com.zm.kilacraftAI.model.event;

import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 服务器事件数据模型
 *
 * <p>对应 {@code kca_server_event} 表的一条记录。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
@Getter
@Builder
public class ServerEvent {

    private final ServerEventTypeEnum eventType;
    private final UUID playerUuid;
    private final UUID targetUuid;
    private final String data;
    private final long createdAt;

    /**
     * 玩家名称（仅通过 JOIN player_profile 查询时填充，直接查询时为 null）
     */
    private final String playerName;

    /**
     * 创建事件（无目标玩家）
     */
    public static ServerEvent of(ServerEventTypeEnum type, UUID playerUuid, String data) {
        return ServerEvent.builder().eventType(type).playerUuid(playerUuid).data(data).createdAt(System.currentTimeMillis()).build();
    }

    /**
     * 创建事件（有目标玩家）
     */
    public static ServerEvent of(ServerEventTypeEnum type, UUID playerUuid, UUID targetUuid, String data) {
        return ServerEvent.builder().eventType(type).playerUuid(playerUuid).targetUuid(targetUuid).data(data).createdAt(System.currentTimeMillis()).build();
    }
}
