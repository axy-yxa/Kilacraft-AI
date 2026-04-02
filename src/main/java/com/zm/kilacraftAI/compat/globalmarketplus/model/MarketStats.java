package com.zm.kilacraftAI.compat.globalmarketplus.model;

import lombok.Getter;

/**
 * 市场统计信息
 */
@Getter
public class MarketStats {
    private final int totalItems;       // 商品总数
    private final int totalSellers;     // 卖家总数

    public MarketStats(int totalItems, int totalSellers) {
        this.totalItems = totalItems;
        this.totalSellers = totalSellers;
    }
}
