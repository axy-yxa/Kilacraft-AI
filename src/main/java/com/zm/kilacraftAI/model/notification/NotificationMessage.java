package com.zm.kilacraftAI.model.notification;

/**
 * 通知消息
 *
 * @param title   消息标题
 * @param content 消息正文（纯文本 + 有限 Markdown）
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public record NotificationMessage(String title, String content) {

    public static NotificationMessage of(String title, String content) {
        return new NotificationMessage(title, content);
    }
}
