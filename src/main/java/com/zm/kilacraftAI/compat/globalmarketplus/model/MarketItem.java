package com.zm.kilacraftAI.compat.globalmarketplus.model;

import lombok.Getter;

/**
 * 商品信息 - 封装单个商品的完整数据
 *
 * @author Zm_Mmm
 * @since 2026-04-02
 */
@Getter
public class MarketItem {
    private final String itemName;      // 物品名称
    private final String displayName;   // 显示名称（带颜色等）
    private final double price;         // 单价
    private final int amount;           // 此价格下的数量

    public MarketItem(String itemName, String displayName, double price, int amount) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.price = price;
        this.amount = amount;
    }
}
