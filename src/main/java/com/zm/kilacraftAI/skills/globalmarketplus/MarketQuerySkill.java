package com.zm.kilacraftAI.skills.globalmarketplus;

import com.zm.kilacraftAI.compat.globalmarketplus.GlobalMarketPlusAPI;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MailItem;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketItem;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketItemDetail;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketStats;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.translate.ItemTranslator;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Map.entry;

/**
 * GlobalMarketPlus 市场查询技能
 *
 * <p>实现只读操作：查询玩家余额、查询市场商品等</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class MarketQuerySkill implements Skill {

    private final SkillConfigManager configManager;

    public MarketQuerySkill() {
        // 获取配置管理器实例
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("globalmarketplus", "MarketQuerySkill") == null) {
            // 保存默认配置到磁盘
            configManager.saveDefaultSkillConfig("globalmarketplus", "MarketQuerySkill");
            // 从磁盘动态加载配置到内存
            configManager.loadSingleSkillConfig("globalmarketplus", "MarketQuerySkill");
        }
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("globalmarketplus", "MarketQuerySkill");
    }

    @Override
    public String getName() {
        return "market_query";
    }

    @Override
    public String getDescription() {
        // 优先使用配置文件中的描述，如果没有则使用默认值
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return java.util.Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return new ArrayList<>();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return !GlobalMarketPlusAPI.isAvailable();
    }

    /**
     * 获取响应消息（从配置文件）
     *
     * @param key            消息键（预定义的标准 key）
     * @param defaultMessage 默认消息
     * @return 配置的消息或默认消息
     */
    protected String getResponseMessage(String key, String defaultMessage) {
        SkillConfig config = getConfig();
        if (config != null && config.getResponseMessages() != null) {
            String message = config.getResponseMessages().get(key);
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return defaultMessage;
    }

    /**
     * 获取响应消息（支持变量替换）
     *
     * @param key            消息键（预定义的标准 key）
     * @param defaultMessage 默认消息
     * @param variables      变量替换 Map（key=变量名，value=变量值）
     * @return 配置的消息或默认消息（已替换变量）
     */
    protected String getResponseMessage(String key, String defaultMessage, Map<String, String> variables) {
        String message = getResponseMessage(key, defaultMessage);

        // 替换变量
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return message;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        try {
            // 根据 LLM 识别的动作执行对应操作
            String action = context.getAction();

            return actionToHandler.getOrDefault(action, this::handleUnknownAction).apply(context);
        } catch (Exception e) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("error", e.getMessage());
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("query_error", "查询失败：" + e.getMessage(), vars)));
        }
    }

    // 初始化映射关系
    private final Map<String, Function<SkillContext, CompletableFuture<SkillResult>>> actionToHandler = Map.ofEntries(
            entry("query_balance", this::queryBalance),
            entry("query_price", this::queryPrices),
            entry("query_items", this::queryMarketItems),
            entry("query_availability", this::queryAvailability),
            entry("query_my_items", this::queryMyItems),
            entry("query_mailbox", this::queryMailbox),
            entry("query_market_stats", this::queryMarketStats)
    );

    private CompletableFuture<SkillResult> handleUnknownAction(SkillContext context) {
        return CompletableFuture.completedFuture(
                SkillResult.failure(getResponseMessage("unknown_action",
                        "抱歉，我还不会查询其他市场信息。你可以问我：'我的余额是多少'、'市场上有什么商品'等"))
        );
    }

    /**
     * 查询玩家余额
     */
    private CompletableFuture<SkillResult> queryBalance(SkillContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("balance_not_player", "请在游戏中使用此功能")));
        }

        // 使用 GlobalMarketPlus API
        double balance = GlobalMarketPlusAPI.getBalance(player);

        if (balance < 0) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("balance_api_error", "无法获取余额信息，请确保 GlobalMarketPlus 插件已正确安装")));
        }

        // 构建变量 Map
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("balance", String.format("%.2f", balance));

        // 构建 data Map，供多步骤任务引用
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("balance", balance);

        return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("balance_success", "§f你的余额为：§a$%.2f", vars), dataMap));
    }

    /**
     * 查询市场价格（支持多物品联合查询和数量）
     *
     * <p>支持的输入格式："物品 1:数量 1，物品 2:数量 2"</p>
     */
    private CompletableFuture<SkillResult> queryPrices(SkillContext context) {
        // 从 LLM 提取的实体中获取物品名称（格式："物品 1:数量 1，物品 2:数量 2"）
        String itemName = context.getEntity("item");

        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("price_no_item", "请指定要查询的物品名称，例如：'钻石的价格'")));
        }

        // 严格按逗号分割（LLM 已被要求多个物品时必须用逗号分隔）
        String[] itemEntries = itemName.split(",\\s*");

        // 获取翻译器实例
        ItemTranslator translator = ItemTranslator.getInstance();

        // 存储查询结果
        List<String> priceResults = new ArrayList<>();
        double totalPrice = 0.0;
        int successCount = 0;

        for (String entry : itemEntries) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            // 解析物品名称和数量（格式："物品：数量"或"物品"）
            String itemNameOnly;
            int quantity = 1; // 默认数量为 1

            int colonIndex = entry.lastIndexOf(':');
            if (colonIndex > 0) {
                // 包含数量信息
                itemNameOnly = entry.substring(0, colonIndex).trim();
                try {
                    quantity = Integer.parseInt(entry.substring(colonIndex + 1).trim());
                    if (quantity <= 0) {
                        quantity = 1; // 防止负数
                    }
                } catch (NumberFormatException e) {
                    // 解析失败使用默认值
                }
            } else {
                // 纯物品名称，数量为 1
                itemNameOnly = entry.trim();
            }

            if (itemNameOnly.isEmpty()) {
                continue;
            }

            // 将中文翻译成英文（用于查询）
            String englishItemName = translator.translateToEnglish(itemNameOnly);

            // 使用 API 获取所有匹配的商品信息
            List<MarketItem> matchingItems = GlobalMarketPlusAPI.getMatchingItems(englishItemName);

            // 如果找不到，尝试用中文名再查一次（兼容情况）
            if (matchingItems.isEmpty()) {
                matchingItems = GlobalMarketPlusAPI.getMatchingItems(itemNameOnly);
            }

            if (!matchingItems.isEmpty()) {
                // 查询成功
                String displayName = translator.translateToChinese(englishItemName);

                // 计算最优总价（从便宜到贵购买 quantity 个，考虑实际库存）
                double totalItemPrice = GlobalMarketPlusAPI.calculateOptimalPrice(englishItemName, quantity);

                // 如果英文名查不到价格，用中文名计算
                if (totalItemPrice < 0) {
                    totalItemPrice = GlobalMarketPlusAPI.calculateOptimalPrice(itemNameOnly, quantity);
                }

                if (totalItemPrice >= 0) {
                    // 获取价格信息用于显示
                    double minPrice = matchingItems.get(0).getPrice();
                    double maxPrice = matchingItems.get(matchingItems.size() - 1).getPrice();

                    if (quantity > 1) {
                        // 根据价格是否相同选择显示方式
                        if (minPrice == maxPrice) {
                            // 所有卖家单价相同
                            priceResults.add(String.format("§f%s × %d: §a$%.2f (单价：$%.2f)",
                                    displayName, quantity, totalItemPrice, minPrice));
                        } else {
                            // 不同卖家单价不同，显示价格区间
                            priceResults.add(String.format("§f%s × %d: §a$%.2f (单价：$%.2f - $%.2f)",
                                    displayName, quantity, totalItemPrice, minPrice, maxPrice));
                        }
                    } else {
                        // 单个物品，显示单价
                        priceResults.add(String.format("§f%s: §a$%.2f", displayName, minPrice));
                    }

                    totalPrice += totalItemPrice;
                    successCount++;
                } else {
                    // 库存不足 - 显示所有在售商品的详细信息
                    priceResults.add(formatInsufficientStockMessage(displayName, quantity, matchingItems));
                    // 注意：库存不足时不增加 successCount，但也不应该导致技能失败
                    // 因为这是一个有效的查询结果，只是无法满足购买需求
                }
            } else {
                // 未找到价格
                priceResults.add(String.format("§f%s: §c未找到价格", itemNameOnly));
            }
        }

        // 修改判断逻辑：只要有匹配的物品（无论库存是否足够），都算成功
        if (successCount == 0 && !priceResults.isEmpty()) {
            // 有物品但库存不足的情况，返回成功（告知用户库存不足）
            StringBuilder sb = new StringBuilder();
            sb.append(getResponseMessage("price_header", "§f商品价格:\n"));

            for (String result : priceResults) {
                sb.append("§7- ").append(result).append("\n");
            }

            return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
        }

        if (successCount == 0) {
            Map<String, String> vars = new LinkedHashMap<>();
            // 提取纯净的物品名称（去掉数量后缀）
            String cleanItemName = itemName;
            int colonIdx = itemName.lastIndexOf(':');
            if (colonIdx > 0) {
                cleanItemName = itemName.substring(0, colonIdx).trim();
            }
            // 翻译为中文显示
            vars.put("item", translator.translateToChinese(cleanItemName));
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("price_not_found", "未找到物品 '{item}' 的价格信息", vars)));
        }

        // 构建响应消息
        StringBuilder sb = new StringBuilder();
        sb.append(getResponseMessage("price_header", "§f商品价格:\n"));

        for (String result : priceResults) {
            sb.append("§7- ").append(result).append("\n");
        }

        // 显示总价
        sb.append(String.format("\n§f总计：§a$%.2f", totalPrice));

        // 构建 data Map，供多步骤任务引用
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("total_price", totalPrice);
        dataMap.put("item_count", successCount);
        // 如果是单个物品查询，添加详细信息
        if (successCount == 1 && priceResults.size() == 1) {
            // 解析物品名称和价格
            String[] parts = itemName.split(",\\s*");
            if (parts.length == 1) {
                String singleItem = parts[0].trim();
                int colonIdx = singleItem.lastIndexOf(':');
                String itemNameOnly = colonIdx > 0 ? singleItem.substring(0, colonIdx).trim() : singleItem;
                dataMap.put("item_name", translator.translateToChinese(itemNameOnly));
                dataMap.put("price", totalPrice);
            }
        }

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString(), dataMap));
    }

    /**
     * 计算商品总库存
     */
    private int getTotalStock(List<MarketItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream().mapToInt(MarketItem::getAmount).sum();
    }

    /**
     * 生成库存不足的详细提示消息
     */
    private String formatInsufficientStockMessage(String displayName, int neededQuantity, List<MarketItem> matchingItems) {
        int totalStock = getTotalStock(matchingItems);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§f%s × %d: §c库存不足 (市场仅有 %d 个)",
                displayName, neededQuantity, totalStock));

        // 显示所有在售商品的价格和数量
        if (!matchingItems.isEmpty()) {
            sb.append("\n§7  在售信息:");
            for (int i = 0; i < matchingItems.size(); i++) {
                MarketItem item = matchingItems.get(i);
                sb.append(String.format("\n§7    [%d] $%.2f × %d个",
                        i + 1, item.getPrice(), item.getAmount()));
            }
        }

        return sb.toString();
    }

    /**
     * 查询市场商品列表
     */
    private CompletableFuture<SkillResult> queryMarketItems(SkillContext context) {
        // 使用 GlobalMarketPlus API
        java.util.List<String> items = GlobalMarketPlusAPI.getAllMarketItems();

        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("items_empty", "§f市场上暂无商品")));
        }

        // 获取翻译器实例
        ItemTranslator translator = ItemTranslator.getInstance();

        StringBuilder sb = new StringBuilder();
        sb.append(getResponseMessage("items_header", "§f市场商品列表:\n"));

        for (String item : items) {
            // 解析物品名称和价格（格式可能是 "DIAMOND: $100.00" 或 "DIAMOND"）
            String itemName;
            String pricePart = "";

            int colonIndex = item.indexOf(':');
            if (colonIndex > 0) {
                // 包含价格信息
                itemName = item.substring(0, colonIndex).trim();
                pricePart = item.substring(colonIndex); // 保留冒号和价格
            } else {
                // 纯物品名称
                itemName = item.trim();
            }

            // 拼接翻译后的名称和价格
            sb.append(getResponseMessage("items_format", "§7- ")).append(translator.translateToChinese(itemName)).append(pricePart).append("\n");
        }
        return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
    }

    /**
     * 查询物品是否在售、在售数量和卖家信息
     */
    private CompletableFuture<SkillResult> queryAvailability(SkillContext context) {
        String itemName = context.getEntity("item");

        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("availability_no_item", "请指定要查询的物品名称")));
        }

        // 去掉数量后缀（如果有）
        int colonIdx = itemName.lastIndexOf(':');
        if (colonIdx > 0) {
            itemName = itemName.substring(0, colonIdx).trim();
        }

        ItemTranslator translator = ItemTranslator.getInstance();
        String englishItemName = translator.translateToEnglish(itemName);

        // 获取详细信息（包含卖家）
        List<MarketItemDetail> details = GlobalMarketPlusAPI.getMatchingItemsDetail(englishItemName);

        // 如果找不到，尝试用中文名再查一次
        if (details.isEmpty()) {
            details = GlobalMarketPlusAPI.getMatchingItemsDetail(itemName);
        }

        if (details.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("§f" + translator.translateToChinese(englishItemName) + " §c目前市场上没有在售"));
        }

        // 物品在售
        String displayName = translator.translateToChinese(englishItemName);
        int totalStock = details.stream().mapToInt(MarketItemDetail::getAmount).sum();
        double minPrice = details.get(0).getPrice();
        double maxPrice = details.get(details.size() - 1).getPrice();

        // 收集卖家名称（去重，保持顺序）
        java.util.Set<String> uniqueSellers = new java.util.LinkedHashSet<>();
        for (MarketItemDetail detail : details) {
            uniqueSellers.add(detail.getSellerName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§f%s §a有在售\n", displayName));
        sb.append(String.format("§7库存：%d 个\n", totalStock));
        sb.append(String.format("§7价格：$%.2f - $%.2f\n", minPrice, maxPrice));
        sb.append("§7卖家：");

        // 显示去重后的卖家名称
        java.util.List<String> sellerList = new java.util.ArrayList<>(uniqueSellers);
        int showCount = Math.min(5, sellerList.size());
        for (int i = 0; i < showCount; i++) {
            if (i > 0) sb.append(", ");
            sb.append(sellerList.get(i));
        }
        if (sellerList.size() > 5) {
            sb.append(String.format(" ... 等%d人", sellerList.size()));
        }

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
    }

    /**
     * 查询玩家自己在售的商品
     */
    private CompletableFuture<SkillResult> queryMyItems(SkillContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("请在游戏中使用此功能"));
        }

        List<MarketItemDetail> myItems = GlobalMarketPlusAPI.getMyMerchandises(player);

        if (myItems.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("§f你没有在售的商品"));
        }

        ItemTranslator translator = ItemTranslator.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§f你在售的商品 (共 %d 个):\n", myItems.size()));

        int showCount = Math.min(10, myItems.size());
        for (int i = 0; i < showCount; i++) {
            MarketItemDetail item = myItems.get(i);
            String displayName = translator.translateToChinese(item.getItemName());
            sb.append(String.format("§7[%d] §f%s × %d §7- §a$%.2f\n",
                    i + 1, displayName, item.getAmount(), item.getPrice()));
        }

        if (myItems.size() > 10) {
            sb.append(String.format("§7... 还有 %d 个商品", myItems.size() - 10));
        }

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
    }

    /**
     * 查询玩家邮箱待领取邮件
     */
    private CompletableFuture<SkillResult> queryMailbox(SkillContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("请在游戏中使用此功能"));
        }

        List<MailItem> mails = GlobalMarketPlusAPI.getMailboxItems(player);

        if (mails.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success("§f你的邮箱是空的"));
        }

        ItemTranslator translator = ItemTranslator.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§f邮箱待领取 (共 %d 封):\n", mails.size()));

        int showCount = Math.min(5, mails.size());
        for (int i = 0; i < showCount; i++) {
            MailItem mail = mails.get(i);
            String displayName = translator.translateToChinese(mail.getItemName());
            sb.append(String.format("§7[%d] §f%s × %d §7来自: §e%s\n",
                    i + 1, displayName, mail.getAmount(),
                    mail.getSenderName() != null ? mail.getSenderName() : "系统"));
        }

        if (mails.size() > 5) {
            sb.append(String.format("§7... 还有 %d 封邮件", mails.size() - 5));
        }

        return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
    }

    /**
     * 查询市场统计信息
     */
    private CompletableFuture<SkillResult> queryMarketStats(SkillContext context) {
        MarketStats stats = GlobalMarketPlusAPI.getMarketStats();

        if (stats.getTotalItems() == 0) {
            return CompletableFuture.completedFuture(SkillResult.success("§f市场暂无商品"));
        }

        String sb = "§f=== 市场统计 ===\n" +
        String.format("§7商品总数: §f%d 个\n", stats.getTotalItems()) +
        String.format("§7卖家数量: §f%d 人", stats.getTotalSellers());
        return CompletableFuture.completedFuture(SkillResult.success(sb));
    }
}
