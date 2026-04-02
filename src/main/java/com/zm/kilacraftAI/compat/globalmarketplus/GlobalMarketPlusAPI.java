package com.zm.kilacraftAI.compat.globalmarketplus;

import com.zm.kilacraftAI.compat.globalmarketplus.model.MailItem;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketItem;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketItemDetail;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import studio.trc.bukkit.globalmarketplus.api.GlobalMarket;
import studio.trc.bukkit.globalmarketplus.api.Mailbox;
import studio.trc.bukkit.globalmarketplus.api.Merchant;
import studio.trc.bukkit.globalmarketplus.hook.GlobalMarketEconomy;
import studio.trc.bukkit.globalmarketplus.mailbox.ItemMail;
import studio.trc.bukkit.globalmarketplus.merchandise.Merchandise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GlobalMarketPlus API 调用工具类
 *
 * <p>封装 GlobalMarketPlus 插件的真实 API 调用</p>
 * <p>包路径：studio.trc.bukkit.globalmarketplus.api</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class GlobalMarketPlusAPI {

    /**
     * 检查 GlobalMarketPlus 是否已安装
     *
     * @return true=已安装，false=未安装
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("GlobalMarketPlus") == null;
    }

    /**
     * 获取 GlobalMarket 实例
     *
     * @return GlobalMarket 实例，如果插件未安装则返回 null
     */
    public static GlobalMarket getGlobalMarket() {
        if (isAvailable()) {
            return null;
        }
        try {
            return GlobalMarket.getGlobalMarket();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取玩家余额（默认货币单位）
     *
     * @param player 玩家对象
     * @return 余额，如果插件未安装则返回 -1
     */
    public static double getBalance(Player player) {
        if (isAvailable() || player == null) {
            return -1;
        }

        try {
            // 使用 Merchant API 获取玩家余额
            Merchant merchant = Merchant.getMerchant(player);
            if (merchant != null) {
                double balance = merchant.getBalance(GlobalMarketEconomy.VAULT);
                if (balance == 0.0) {
                    // 默认货币的余额
                    balance = merchant.getDefaultBalance();
                }
                return balance;
            }
            return -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * 获取匹配的物品列表（包含完整商品信息）
     *
     * @param itemName 物品名称
     * @return 商品信息列表（已按价格从低到高排序），如果未找到则返回空列表
     */
    public static List<MarketItem> getMatchingItems(String itemName) {
        if (isAvailable() || itemName == null) {
            return new ArrayList<>();
        }

        try {
            GlobalMarket market = getGlobalMarket();
            if (market == null) {
                return new ArrayList<>();
            }

            // 获取所有市场商品
            List<Merchandise> allItems = market.getMerchandises();
            if (allItems == null || allItems.isEmpty()) {
                return new ArrayList<>();
            }

            // 查找匹配的物品（按名称模糊匹配）
            String lowerItemName = itemName.toLowerCase();
            List<Merchandise> matchedItems = allItems.stream().filter(merch -> merch != null && merch.getItem() != null).filter(merch -> {
                String displayName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName().toLowerCase() : merch.getItem().getType().name().toLowerCase();
                return displayName.contains(lowerItemName);
            }).toList();

            if (matchedItems.isEmpty()) {
                return new ArrayList<>();
            }

            // 优化：如果找到多个结果，优先选择精确匹配的（排除包含其他词的）
            // 例如：搜索"钻石"，应该排除"钻石剑"、"钻石镐"等
            List<Merchandise> exactMatches = matchedItems.stream().filter(merch -> {
                String displayName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName().toLowerCase() : merch.getItem().getType().name().toLowerCase();
                // 精确匹配：物品名等于搜索词，或者是搜索词的复数形式
                return displayName.equals(lowerItemName) || displayName.equals(lowerItemName + "s");
            }).toList();

            // 如果有精确匹配，只使用精确匹配的结果
            List<Merchandise> finalMatches = !exactMatches.isEmpty() ? exactMatches : matchedItems;

            // 转换为 MarketItem 列表
            List<MarketItem> result = new ArrayList<>();
            for (Merchandise merch : finalMatches) {
                String rawItemName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName() : merch.getItem().getType().name();

                // 使用 getInitialAmount() 获取此 listing 的实际数量
                int amount = merch.getInitialAmount();

                result.add(new MarketItem(rawItemName, rawItemName, merch.getPrice(), amount));
            }

            // 按价格从低到高排序
            result.sort(Comparator.comparingDouble(MarketItem::getPrice));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 获取匹配物品的详细信息（包含卖家信息）
     *
     * @param itemName 物品名称
     * @return 商品详细信息列表（已按价格从低到高排序）
     */
    public static List<MarketItemDetail> getMatchingItemsDetail(String itemName) {
        if (isAvailable() || itemName == null) {
            return new ArrayList<>();
        }

        try {
            GlobalMarket market = getGlobalMarket();
            if (market == null) {
                return new ArrayList<>();
            }

            List<Merchandise> allItems = market.getMerchandises();
            if (allItems == null || allItems.isEmpty()) {
                return new ArrayList<>();
            }

            String lowerItemName = itemName.toLowerCase();
            List<Merchandise> matchedItems = allItems.stream().filter(merch -> merch != null && merch.getItem() != null).filter(merch -> {
                String displayName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName().toLowerCase() : merch.getItem().getType().name().toLowerCase();
                return displayName.contains(lowerItemName);
            }).toList();

            if (matchedItems.isEmpty()) {
                return new ArrayList<>();
            }

            List<Merchandise> exactMatches = matchedItems.stream().filter(merch -> {
                String displayName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName().toLowerCase() : merch.getItem().getType().name().toLowerCase();
                return displayName.equals(lowerItemName) || displayName.equals(lowerItemName + "s");
            }).toList();

            List<Merchandise> finalMatches = !exactMatches.isEmpty() ? exactMatches : matchedItems;

            List<MarketItemDetail> result = new ArrayList<>();
            for (Merchandise merch : finalMatches) {
                String rawItemName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName() : merch.getItem().getType().name();

                int amount = merch.getInitialAmount();
                String sellerName = merch.getOwnerName();

                result.add(new MarketItemDetail(rawItemName, rawItemName, merch.getPrice(), amount, sellerName, 0));
            }

            result.sort(Comparator.comparingDouble(MarketItemDetail::getPrice));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 计算购买指定数量物品的最优总价（从便宜到贵购买，考虑实际库存）
     *
     * @param itemName       物品名称
     * @param quantityNeeded 需要购买的数量
     * @return 最优总价，如果库存不足则返回 -1
     */
    public static double calculateOptimalPrice(String itemName, int quantityNeeded) {
        if (quantityNeeded <= 0) {
            return 0.0;
        }

        List<MarketItem> items = getMatchingItems(itemName);
        if (items.isEmpty()) {
            return -1;
        }

        double totalPrice = 0.0;
        int remaining = quantityNeeded;

        // 从便宜到贵依次购买（已排序）
        for (MarketItem item : items) {
            if (remaining <= 0) {
                break;
            }

            // 此 listing 可以购买的数量
            int canBuy = Math.min(remaining, item.getAmount());
            totalPrice += canBuy * item.getPrice();
            remaining -= canBuy;
        }

        // 如果还有剩余需求，说明库存不足
        if (remaining > 0) {
            return -1;
        }
        return totalPrice;
    }

    /**
     * 获取所有市场商品列表
     *
     * @return 商品列表（格式：物品名: $价格）
     */
    public static List<String> getAllMarketItems() {
        if (isAvailable()) {
            return null;
        }

        try {
            GlobalMarket market = getGlobalMarket();
            if (market == null) {
                return null;
            }

            // 获取所有市场商品
            List<Merchandise> allItems = market.getMerchandises();
            if (allItems == null || allItems.isEmpty()) {
                return new ArrayList<>();
            }

            // 转换为字符串列表
            return allItems.stream().filter(merch -> merch != null && merch.getItem() != null).map(merch -> {
                String itemName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName() : merch.getItem().getType().name();
                return itemName + ": $" + String.format("%.2f", merch.getPrice());
            }).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取玩家自己在售的商品
     *
     * @param player 玩家对象
     * @return 玩家商品列表
     */
    public static List<MarketItemDetail> getMyMerchandises(Player player) {
        if (isAvailable() || player == null) {
            return new ArrayList<>();
        }

        try {
            Merchant merchant = Merchant.getMerchant(player);
            if (merchant == null) {
                return new ArrayList<>();
            }

            // 获取玩家的商品
            List<Merchandise> myItems = merchant.getMerchandises();
            if (myItems == null || myItems.isEmpty()) {
                return new ArrayList<>();
            }

            List<MarketItemDetail> result = new ArrayList<>();
            for (Merchandise merch : myItems) {
                String rawItemName = merch.getItem().hasItemMeta() && merch.getItem().getItemMeta().hasDisplayName() ? merch.getItem().getItemMeta().getDisplayName() : merch.getItem().getType().name();
                int amount = merch.getInitialAmount();
                result.add(new MarketItemDetail(rawItemName, rawItemName, merch.getPrice(), amount, merch.getOwnerName(), 0));
            }

            // 按价格排序
            result.sort(Comparator.comparingDouble(MarketItemDetail::getPrice));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 获取玩家邮箱待领取邮件
     *
     * @param player 玩家对象
     * @return 邮件列表
     */
    public static List<MailItem> getMailboxItems(Player player) {
        if (isAvailable() || player == null) {
            return new ArrayList<>();
        }

        try {
            Merchant merchant = Merchant.getMerchant(player);
            if (merchant == null) {
                return new ArrayList<>();
            }

            // 获取邮箱
            Mailbox mailbox = merchant.getMailbox();
            if (mailbox == null) {
                return new ArrayList<>();
            }

            // 获取所有邮件
            List<ItemMail> mails = mailbox.getItemMails();
            if (mails == null || mails.isEmpty()) {
                return new ArrayList<>();
            }

            List<MailItem> result = new ArrayList<>();
            for (ItemMail mail : mails) {
                String itemName = mail.getItem().hasItemMeta() && mail.getItem().getItemMeta().hasDisplayName() ? mail.getItem().getItemMeta().getDisplayName() : mail.getItem().getType().name();
                int amount = mail.getItem().getAmount();
                String senderName = mail.getSenderName();
                long sendingTime = mail.getSendingTime();
                result.add(new MailItem(itemName, amount, senderName, sendingTime));
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 获取市场统计信息
     *
     * @return 市场统计
     */
    public static MarketStats getMarketStats() {
        if (isAvailable()) {
            return new MarketStats(0, 0);
        }

        try {
            GlobalMarket market = getGlobalMarket();
            if (market == null) {
                return new MarketStats(0, 0);
            }

            // 获取所有商品
            List<Merchandise> allItems = market.getMerchandises();
            if (allItems == null || allItems.isEmpty()) {
                return new MarketStats(0, 0);
            }

            int totalItems = allItems.size();

            // 统计卖家数量
            Set<UUID> sellers = new HashSet<>();
            for (Merchandise merch : allItems) {
                if (merch.getOwnerUUID() != null) {
                    sellers.add(merch.getOwnerUUID());
                }
            }

            return new MarketStats(totalItems, sellers.size());
        } catch (Exception e) {
            e.printStackTrace();
            return new MarketStats(0, 0);
        }
    }
}
