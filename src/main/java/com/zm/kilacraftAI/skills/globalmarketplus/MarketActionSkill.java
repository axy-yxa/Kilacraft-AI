package com.zm.kilacraftAI.skills.globalmarketplus;

import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.compat.globalmarketplus.GlobalMarketPlusAPI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.config.SkillConfig;
import com.zm.kilacraftAI.translate.ItemTranslator;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 全球市场操作技能 - 搜索商品 & 上架手中商品
 *
 * @author Zm_Mmm
 * @since 2026-04-27
 */
public class MarketActionSkill implements Skill {

    private final SkillConfigManager configManager;

    public MarketActionSkill() {
        this.configManager = SkillConfigManager.getInstance();

        if (configManager != null && configManager.getSkillConfig("globalmarketplus", "MarketActionSkill") == null) {
            configManager.saveDefaultSkillConfig("globalmarketplus", "MarketActionSkill");
            configManager.loadSingleSkillConfig("globalmarketplus", "MarketActionSkill");
        }
    }

    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig("globalmarketplus", "MarketActionSkill");
    }

    @Override
    public String getName() {
        return "market_action";
    }

    @Override
    public String getDescription() {
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
        // GlobalMarketPlus 必须已安装且玩家在线
        return !GlobalMarketPlusAPI.isAvailable() && context.getPlayer() != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        try {
            String action = context.getAction();
            return switch (action) {
                case "search_item" -> searchItem(context);
                case "sell_item" -> sellItem(context);
                default ->
                        CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 未知的市场操作: {}", action)));
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 市场操作失败: {}", e.getMessage())));
        }
    }

    // ==================== search_item ====================

    /**
     * 搜索商品（打开购买 GUI）
     *
     * <p>流程：获取物品名 → 翻译为英文 → Material 强校验 → API 预校验商品存在 → 代执行 /market search</p>
     */
    private CompletableFuture<SkillResult> searchItem(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 仅限在线玩家使用"));
        }

        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 缺少参数: item(物品名称)"));
        }

        // 翻译为英文 Material 名
        ItemTranslator translator = ItemTranslator.getInstance();
        String englishName = translator.translateToEnglish(itemName);

        // Material 强校验
        Material material = Material.matchMaterial(englishName);
        if (material == null) {
            // 尝试直接用原名匹配
            material = Material.matchMaterial(itemName.toUpperCase());
        }
        if (material == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 无法识别的物品: {}，请使用标准的物品名称", itemName)));
        }

        final String materialName = material.name();
        final String displayName = translator.translateToChinese(materialName);

        // 预校验：API 查询市场上是否有该商品，避免命令执行后返回虚假成功
        if (GlobalMarketPlusAPI.getMatchingItems(materialName).isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("市场上没有找到 {} 的商品", displayName)));
        }

        // 预校验通过 → 代执行搜索命令
        return GlobalMarketPlusAPI.searchItem(player, materialName).thenApply(success -> {
            if (success) {
                return SkillResult.success(I18nService.tr("已为你打开 {} 的搜索页面，请在弹出的 GUI 中点击购买", displayName));
            } else {
                return SkillResult.failure("[FAILURE] 搜索失败，可能没有权限或命令不可用");
            }
        }).exceptionally(ex -> {
            PluginLogger.warn("市场操作", I18nService.tr("搜索商品异常: {} - {}", materialName, ex.getMessage()), ex);
            return SkillResult.failure(I18nService.tr("[FAILURE] 搜索商品时发生异常: {}", ex.getMessage()));
        });
    }

    // ==================== sell_item ====================

    /**
     * 上架手中商品（引导式）
     *
     * <p>流程：</p>
     * <ol>
     *   <li>在主线程读取玩家手持物品（线程安全）</li>
     *   <li>检查价格参数 → 缺少则返回参考价让玩家确认</li>
     *   <li>检查数量参数 → 缺少则使用物品堆叠数量</li>
     *   <li>代执行 /market sell [price] [quantity]</li>
     * </ol>
     */
    private CompletableFuture<SkillResult> sellItem(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 仅限在线玩家使用"));
        }

        // 在主线程/实体线程读取手持物品（线程安全）
        ItemStack handItem;
        try {
            handItem = FoliaCompat.callSyncOnEntity(player, player::getInventory, 5).getItemInMainHand();
        } catch (Exception e) {
            PluginLogger.warn("市场操作", I18nService.tr("读取玩家手持物品失败: {}", player.getName()), e);
            return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 读取手持物品失败，请稍后重试"));
        }

        // 检查手持物品
        if (handItem == null || handItem.getType() == Material.AIR) {
            return CompletableFuture.completedFuture(SkillResult.failure("[NEED_INFO] 请先将需要上架的物品拿在主手中"));
        }

        String itemTypeName = handItem.getType().name();
        ItemTranslator translator = ItemTranslator.getInstance();
        String displayName = translator.translateToChinese(itemTypeName);
        int stackAmount = handItem.getAmount();

        // 解析价格参数
        String priceStr = context.getEntity("price");
        Double price = null;
        if (priceStr != null && !priceStr.isEmpty()) {
            try {
                price = Double.parseDouble(priceStr);
                if (price <= 0) {
                    return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 价格必须大于 0"));
                }
            } catch (NumberFormatException e) {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 价格格式不正确: {}", priceStr)));
            }
        }

        // 解析数量参数
        String quantityStr = context.getEntity("quantity");
        int quantity = stackAmount; // 默认为物品堆叠数量
        if (quantityStr != null && !quantityStr.isEmpty()) {
            try {
                quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0) {
                    return CompletableFuture.completedFuture(SkillResult.failure("[FAILURE] 数量必须大于 0"));
                }
                if (quantity > stackAmount) {
                    return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 数量 {} 超过手中物品数量 {}", quantity, stackAmount)));
                }
            } catch (NumberFormatException e) {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[FAILURE] 数量格式不正确: {}", quantityStr)));
            }
        }

        // 价格缺失 → 引导补充
        if (price == null) {
            return buildPriceGuidance(itemTypeName, displayName, quantity);
        }

        // 全部参数齐备 → 执行上架
        final double finalPrice = price;
        final int finalQuantity = quantity;

        return GlobalMarketPlusAPI.sellItem(player, finalPrice, finalQuantity).thenApply(success -> {
            if (success) {
                return SkillResult.success(I18nService.tr("已上架 {} x{}，单价: ${}", displayName, finalQuantity, String.format("%.2f", finalPrice)));
            } else {
                return SkillResult.failure("[FAILURE] 上架失败，可能没有权限、物品在黑名单中或命令不可用");
            }
        }).exceptionally(ex -> {
            PluginLogger.warn("市场操作", I18nService.tr("上架商品异常: {} - {}", itemTypeName, ex.getMessage()), ex);
            return SkillResult.failure(I18nService.tr("[FAILURE] 上架商品时发生异常: {}", ex.getMessage()));
        });
    }

    /**
     * 构建价格引导消息
     *
     * <p>查询市场参考价，引导玩家补充价格参数。</p>
     */
    private CompletableFuture<SkillResult> buildPriceGuidance(String itemTypeName, String displayName, int quantity) {
        double refPrice = GlobalMarketPlusAPI.getItemReferencePrice(itemTypeName);

        if (refPrice > 0) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[NEED_INFO] 你手中的 {} x{}，当前市场最低价为 ${}。请告诉我你希望以多少钱的单价上架？", displayName, quantity, String.format("%.2f", refPrice))));
        } else {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("[NEED_INFO] 你手中的 {} x{}，市场暂无参考价。请告诉我你希望以多少钱的单价上架？", displayName, quantity)));
        }
    }
}
