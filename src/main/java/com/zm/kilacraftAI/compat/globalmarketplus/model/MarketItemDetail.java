package com.zm.kilacraftAI.compat.globalmarketplus.model;

import lombok.Getter;

/**
 * 商品详细信息 - 包含卖家信息
 */
@Getter
public class MarketItemDetail {
    private final String itemName;      // 物品名称
    private final String displayName;   // 显示名称
    private final double price;         // 单价
    private final int amount;           // 数量
    private final String sellerName;    // 卖家名称
    private final long uid;             // 商品UID

    public MarketItemDetail(String itemName, String displayName, double price, int amount, String sellerName, long uid) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.price = price;
        this.amount = amount;
        this.sellerName = sellerName;
        this.uid = uid;
    }
}
