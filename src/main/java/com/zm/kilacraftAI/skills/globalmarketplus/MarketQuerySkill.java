package com.zm.kilacraftAI.skills.globalmarketplus;

import com.zm.kilacraftAI.compat.globalmarketplus.GlobalMarketPlusAPI;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.config.SkillConfig;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.translate.ItemTranslator;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GlobalMarketPlus 市场查询技能
 *
 * <p>实现只读操作：查询玩家余额、查询市场商品等</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class MarketQuerySkill implements Skill {

    private final SkillConfig config;

    public MarketQuerySkill() {
        // 从配置管理器加载配置
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        this.config = configManager != null ? configManager.getSkillConfig("globalmarketplus", "MarketQuerySkill") : null;

        // 如果配置不存在，保存默认配置
        if (this.config == null && configManager != null) {
            saveDefaultConfig(configManager);
        }
    }

    /**
     * 保存默认配置文件
     */
    private void saveDefaultConfig(SkillConfigManager configManager) {
        // 使用 LinkedHashMap 保持插入顺序
        Map<String, String> actionDescriptions = new LinkedHashMap<>();
        actionDescriptions.put("query_balance", "查询玩家账户余额，当用户问'我有多少钱'时使用");
        actionDescriptions.put("query_price", "查询指定物品的市场价格，当用户问'钻石多少钱'时使用");
        actionDescriptions.put("query_items", "查询市场上架的商品列表，当用户问'市场上有什么'时使用");

        // 使用 LinkedHashMap 保持插入顺序
        Map<String, String> responseMessages = new LinkedHashMap<>();
        responseMessages.put("unknown_action", "抱歉，我还不会查询其他市场信息。你可以问我：'我的余额是多少'、'市场上有什么商品'等");
        responseMessages.put("query_error", "查询失败：{error}");
        responseMessages.put("balance_not_player", "请在游戏中使用此功能");
        responseMessages.put("balance_api_error", "无法获取余额信息，请确保 GlobalMarketPlus 插件已正确安装");
        responseMessages.put("balance_success", "§f你的余额为：§a${balance}");
        responseMessages.put("price_no_item", "请指定要查询的物品名称，例如：'钻石的价格'");
        responseMessages.put("price_not_found", "未找到物品 '{item}' 的价格信息");
        responseMessages.put("price_success", "§f{item} 当前价格：§a${price}");
        responseMessages.put("items_empty", "§f市场上暂无商品");
        responseMessages.put("items_header", "§f市场商品列表:\n");
        responseMessages.put("items_format", "§7- ");

        configManager.saveDefaultSkillConfig("globalmarketplus", "MarketQuerySkill", "查询全球市场插件信息，包括玩家余额、市场价格、商品列表等只读操作。当用户询问金钱、余额、物价、商品价格、市场有什么物品时使用此技能。", actionDescriptions, responseMessages);
    }

    @Override
    public String getName() {
        return "market_query";
    }

    @Override
    public String getDescription() {
        // 优先使用配置文件中的描述，如果没有则使用默认值
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return "查询全球市场插件信息，包括玩家余额、市场价格、商品列表等只读操作。当用户询问金钱、余额、物价、商品价格、市场有什么物品时使用此技能。";
    }

    @Override
    public SkillConfig getSkillConfig() {
        return config;
    }

    /**
     * 获取响应消息（从配置文件）
     *
     * @param key            消息键（预定义的标准 key）
     * @param defaultMessage 默认消息
     * @return 配置的消息或默认消息
     */
    protected String getResponseMessage(String key, String defaultMessage) {
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

            return switch (action) {
                case "query_balance" -> queryBalance(context);
                case "query_price" -> queryPrices(context);
                case "query_items" -> queryMarketItems(context);
                case null, default ->
                        CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("unknown_action", "抱歉，我还不会查询其他市场信息。你可以问我：'我的余额是多少'、'市场上有什么商品'等")));
            };
        } catch (Exception e) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("error", e.getMessage());
            return CompletableFuture.completedFuture(SkillResult.failure(getResponseMessage("query_error", "查询失败：" + e.getMessage(), vars)));
        }
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

        return CompletableFuture.completedFuture(SkillResult.success(getResponseMessage("balance_success", "§f你的余额为：§a$%.2f", vars)));
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
            List<GlobalMarketPlusAPI.MarketItem> matchingItems = GlobalMarketPlusAPI.getMatchingItems(englishItemName);
            
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
                    double minPrice = matchingItems.getFirst().getPrice();
                    double maxPrice = matchingItems.getLast().getPrice();
                    
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
                priceResults.add(String.format("§f%s: §c未找到价格", translator.translateToEnglish(itemNameOnly)));
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
            vars.put("item", itemName);
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
        
        return CompletableFuture.completedFuture(SkillResult.success(sb.toString()));
    }
    
    /**
     * 计算商品总库存
     */
    private int getTotalStock(List<GlobalMarketPlusAPI.MarketItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream().mapToInt(GlobalMarketPlusAPI.MarketItem::getAmount).sum();
    }
    
    /**
     * 生成库存不足的详细提示消息
     */
    private String formatInsufficientStockMessage(String displayName, int neededQuantity, List<GlobalMarketPlusAPI.MarketItem> matchingItems) {
        int totalStock = getTotalStock(matchingItems);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§f%s × %d: §c库存不足 (市场仅有 %d 个)", 
            displayName, neededQuantity, totalStock));
        
        // 显示所有在售商品的价格和数量
        if (!matchingItems.isEmpty()) {
            sb.append("\n§7  在售信息:");
            for (int i = 0; i < matchingItems.size(); i++) {
                GlobalMarketPlusAPI.MarketItem item = matchingItems.get(i);
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
}
