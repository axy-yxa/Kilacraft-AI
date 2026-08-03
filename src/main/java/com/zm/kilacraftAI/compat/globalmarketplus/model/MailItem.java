package com.zm.kilacraftAI.compat.globalmarketplus.model;

import lombok.Getter;

/**
 * 邮件信息 - 封装邮件数据
 *
 * @author Zm_Mmm
 * @since 2026-04-02
 */
@Getter
public class MailItem {
    private final String itemName;      // 物品名称
    private final int amount;           // 数量
    private final String senderName;    // 发送者名称
    private final long sendingTime;     // 发送时间

    public MailItem(String itemName, int amount, String senderName, long sendingTime) {
        this.itemName = itemName;
        this.amount = amount;
        this.senderName = senderName;
        this.sendingTime = sendingTime;
    }
}
