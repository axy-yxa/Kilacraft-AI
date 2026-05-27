package com.zm.kilacraftAI.service.notification;

import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.model.notification.NotificationResult;
import okhttp3.OkHttpClient;

/**
 * 通知渠道接口
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public interface NotificationChannel {

    /**
     * 渠道类型标识（对应 YAML 配置中的 type 字段）
     */
    String type();

    /**
     * 发送通知消息
     *
     * @param client  共享的 HTTP 客户端（由 NotificationService 提供）
     * @param message 标准通知消息
     * @return 发送结果
     */
    NotificationResult send(OkHttpClient client, NotificationMessage message);

    /**
     * 测试连接是否可用（发送一条测试消息）
     *
     * @param client 共享的 HTTP 客户端
     * @return 测试结果
     */
    NotificationResult testConnection(OkHttpClient client);

    /**
     * 获取 webhook URL（脱敏后，用于日志输出）
     */
    String getMaskedUrl();
}
