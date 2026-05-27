package com.zm.kilacraftAI.model.notification;

/**
 * 通知发送结果
 *
 * @param success 是否成功
 * @param message 结果描述（成功或失败原因）
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public record NotificationResult(boolean success, String message) {

    public static NotificationResult ok(String message) {
        return new NotificationResult(true, message);
    }

    public static NotificationResult fail(String message) {
        return new NotificationResult(false, message);
    }
}
