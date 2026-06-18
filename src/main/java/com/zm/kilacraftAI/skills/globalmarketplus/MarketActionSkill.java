package com.zm.kilacraftAI.skills.globalmarketplus;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.compat.globalmarketplus.GlobalMarketPlusAPI;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MailItem;
import com.zm.kilacraftAI.compat.globalmarketplus.model.MarketItemDetail;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 全球市场操作技能 - 搜索商品、上架/下架、邮箱领取、转账、拍卖、批量出售/收购
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
        return Collections.emptyMap();
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
    public String getRequiredPermission() {
        return PluginPermissionEnum.MARKET_ACTION.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        // GlobalMarketPlus 必须已安装且玩家在线
        return GlobalMarketPlusAPI.isAvailable() && context.getPlayer() != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // Skill 级权限校验（isAvailable 已保证 GMP 可用且玩家在线）
        Player player = context.getPlayer();
        if (!PluginPermissionEnum.MARKET_ACTION.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.MARKET_ACTION.getNode())));
        }

        try {
            String action = context.getAction();
            return switch (action) {
                case "search_item" -> searchItem(context);
                case "sell_item" -> sellItem(context);
                case "pickup_mail" -> pickupMail(context);
                case "buy_item" -> buyItem(context);
                case "cancel_listing" -> cancelListing(context);
                case "transfer_money" -> transferMoney(context);
                case "auction_item" -> auctionItem(context);
                case "sell_inventory" -> sellInventory(context);
                case "buy_inventory" -> buyInventory(context);
                default ->
                        CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知的市场操作: {}", action)));
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("市场操作失败: {}", e.getMessage())));
        }
    }

    /**
     * 校验玩家在线，不在线则返回失败结果
     */
    private Player requireOnlinePlayer(SkillContext context) {
        return context.getPlayer();
    }

    /**
     * 玩家不在线的统一失败结果
     */
    private static CompletableFuture<SkillResult> offlineFailure() {
        return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));
    }

    /**
     * 在主线程/实体线程读取玩家主手物品（线程安全）
     *
     * <p>整个 getInventory().getItemInMainHand() 操作都在实体线程内完成，
     * 避免将 PlayerInventory 引用泄漏到异步线程。</p>
     *
     * @param player 玩家
     * @return 主手物品，失败时返回 null（调用方应通过 isHandEmpty 判断）
     */
    private ItemStack readHandItem(Player player) {
        try {
            return FoliaCompat.callSyncOnEntity(player, () -> player.getInventory().getItemInMainHand(), 5);
        } catch (Exception e) {
            PluginLoggerUtil.warn("市场插件", I18nService.tr("读取玩家手持物品失败: {}", player.getName()), e);
            return null;
        }
    }

    /**
     * 解析金额：剥离前导 $ 与首尾空白后转 double（玩家可能带 $ 报价）。
     */
    private static double parseMoney(String raw) {
        return Double.parseDouble(raw.trim().replaceFirst("^\\$", "").trim());
    }

    private Double parsePrice(SkillContext context) {
        String priceStr = context.getEntity("price");
        if (priceStr == null || priceStr.isEmpty()) {
            return null;
        }
        try {
            double price = parseMoney(priceStr);
            if (price <= 0 || !Double.isFinite(price)) {
                return Double.NEGATIVE_INFINITY; // 标记为"值不合法"
            }
            return price;
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * 价格不合法的统一失败结果
     */
    private CompletableFuture<SkillResult> invalidPriceFailure(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("价格必须大于 0"));
        }
        return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("价格格式不正确: {}", priceStr)));
    }

    /**
     * 解析数量参数
     *
     * @param context     技能上下文
     * @param maxQuantity 数量上限（如手中物品堆叠数），传 -1 表示不限制
     * @return 解析结果：正数=有效数量，0=参数缺失，-1=格式错误，-2=超出范围，-3=非正数
     */
    private int parseQuantity(SkillContext context, int maxQuantity) {
        String quantityStr = context.getEntity("quantity");
        if (quantityStr == null || quantityStr.isEmpty()) {
            return 0; // 参数缺失
        }
        try {
            int quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                return -3; // 非正数
            }
            if (maxQuantity > 0 && quantity > maxQuantity) {
                return -2; // 超出范围
            }
            return quantity;
        } catch (NumberFormatException e) {
            return -1; // 格式错误
        }
    }

    /**
     * 数量不合法的统一失败结果
     */
    private CompletableFuture<SkillResult> invalidQuantityFailure(int errorCode, String quantityStr, int maxQuantity) {
        return switch (errorCode) {
            case -1 ->
                    CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("数量格式不正确: {}", quantityStr)));
            case -2 ->
                    CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("数量 {} 超过手中物品数量 {}", quantityStr, maxQuantity)));
            default -> CompletableFuture.completedFuture(SkillResult.failure("数量必须大于 0"));
        };
    }

    /**
     * 包装命令执行结果：成功/失败/异常
     *
     * @param future      API 返回的 CompletableFuture
     * @param successMsg  成功消息模板（可含 {} 占位符）
     * @param successArgs 成功消息参数
     * @param failMsg     失败消息（静态，不含 {}）
     * @param errorKey    异常日志中的操作标识
     * @param <T>         API 返回类型（Boolean）
     */
    private <T> CompletableFuture<SkillResult> handleCommandResult(CompletableFuture<Boolean> future, String successMsg, Object[] successArgs, String failMsg, String errorKey) {
        return future.thenApply(success -> {
            if (success) {
                return SkillResult.success(I18nService.tr(successMsg, successArgs));
            } else {
                return SkillResult.failure(failMsg);
            }
        }).exceptionally(ex -> {
            PluginLoggerUtil.warn("市场插件", I18nService.tr("{}异常: {}", errorKey, ex.getMessage()), ex);
            return SkillResult.failure(I18nService.tr("{}时发生异常: {}", errorKey, ex.getMessage()));
        });
    }

    private CompletableFuture<SkillResult> searchItem(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少参数: item(物品名称)"));
        }

        // 翻译为英文 Material 名
        ItemTranslator translator = ItemTranslator.getInstance();
        String englishName = translator.translateToEnglish(itemName);

        Material material = Material.matchMaterial(englishName);
        if (material == null) {
            material = Material.matchMaterial(itemName.toUpperCase());
        }
        if (material == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("无法识别的物品: {}，请使用标准的物品名称", itemName)));
        }

        final String materialName = material.name();
        final String displayName = translator.translateToChinese(materialName);

        // 预校验：API 查询市场上是否有该商品
        if (GlobalMarketPlusAPI.getMatchingItems(materialName).isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("市场上没有找到 {} 的商品", displayName)));
        }

        return handleCommandResult(GlobalMarketPlusAPI.searchItem(player, materialName), "已为你打开 {} 的搜索页面，请在弹出的 GUI 中点击购买", new Object[]{displayName}, "搜索失败，可能没有权限或命令不可用", "搜索商品");
    }

    private CompletableFuture<SkillResult> sellItem(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        ItemStack handItem = readHandItem(player);
        if (handItem == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("读取手持物品失败，请稍后重试"));
        }
        if (handItem.getType() == Material.AIR) {
            return CompletableFuture.completedFuture(SkillResult.needInfo("请先将需要上架的物品拿在主手中"));
        }

        String itemTypeName = handItem.getType().name();
        ItemTranslator translator = ItemTranslator.getInstance();
        String displayName = translator.translateToChinese(itemTypeName);
        int stackAmount = handItem.getAmount();

        // 解析价格
        Double price = parsePrice(context);
        if (price != null && price == Double.NEGATIVE_INFINITY) {
            return invalidPriceFailure(context.getEntity("price"));
        }

        // 解析数量
        int quantity = parseQuantity(context, stackAmount);
        if (quantity < 0) {
            return invalidQuantityFailure(quantity, context.getEntity("quantity"), stackAmount);
        }
        if (quantity == 0) quantity = stackAmount; // 默认为物品堆叠数量

        // 价格缺失 → 引导补充
        if (price == null) {
            return buildPriceGuidance(itemTypeName, displayName, quantity);
        }

        // 余额预检查（上架可能需缴纳税额，余额为 0 时 GMP 可能拒绝）
        double balance = GlobalMarketPlusAPI.getBalance(player);
        if (balance == 0) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("余额不足，上架商品可能需要缴纳税额，当前余额为 $0")));
        }

        // 执行上架
        final double finalPrice = price;
        final int finalQuantity = quantity;
        return handleCommandResult(GlobalMarketPlusAPI.sellItem(player, finalPrice, finalQuantity), "已上架 {} x{}，单价: ${}", new Object[]{displayName, finalQuantity, String.format("%.2f", finalPrice)}, "上架失败，可能没有权限、物品在黑名单中、余额不足以支付税额或命令不可用", "上架商品");
    }

    private CompletableFuture<SkillResult> pickupMail(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        String target = context.getEntity("target");
        if (target == null || target.isEmpty()) {
            target = "all";
        }

        List<MailItem> mails = GlobalMarketPlusAPI.getMailboxItems(player);
        if (mails.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("你的邮箱里没有待领取的物品")));
        }

        if ("all".equalsIgnoreCase(target)) {
            final int totalItems = mails.size();
            return handleCommandResult(GlobalMarketPlusAPI.pickupMail(player, "all"), "已成功领取邮箱中所有物品（共 {} 件）", new Object[]{totalItems}, "领取邮件失败，可能背包已满或没有权限", "领取邮件");
        }

        // 指定 UID 领取
        return handleCommandResult(GlobalMarketPlusAPI.pickupMail(player, target), "已成功领取指定邮件", new Object[]{}, "领取邮件失败，请检查 UID 是否正确", "领取邮件");
    }

    private CompletableFuture<SkillResult> buyItem(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        ItemStack handItem = readHandItem(player);
        if (handItem == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("读取手持物品失败，请稍后重试"));
        }
        if (handItem.getType() == Material.AIR) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请先将需要收购的物品拿在主手中")));
        }

        String itemTypeName = handItem.getType().name();
        ItemTranslator translator = ItemTranslator.getInstance();
        String displayName = translator.translateToChinese(itemTypeName);

        // 解析价格
        Double price = parsePrice(context);
        if (price != null && price == Double.NEGATIVE_INFINITY) {
            return invalidPriceFailure(context.getEntity("price"));
        }

        // 解析数量（默认 1）
        int quantity = parseQuantity(context, -1);
        if (quantity < 0) {
            return invalidQuantityFailure(quantity, context.getEntity("quantity"), -1);
        }
        if (quantity == 0) quantity = 1;

        // 价格缺失 → 引导补充
        if (price == null) {
            double refPrice = GlobalMarketPlusAPI.getItemReferencePrice(itemTypeName);
            if (refPrice > 0) {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你想以多少的单价收购 {} x{}？市场当前参考价: ${}", displayName, quantity, String.format("%.2f", refPrice))));
            } else {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你想以多少的单价收购 {} x{}？市场暂无参考价", displayName, quantity)));
            }
        }

        // 余额预检查
        double balance = GlobalMarketPlusAPI.getBalance(player);
        double totalCost = price * quantity;
        if (balance >= 0 && balance < totalCost) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("余额不足，需要 ${} 但当前余额 ${}", String.format("%.2f", totalCost), String.format("%.2f", balance))));
        }

        final double finalPrice = price;
        final int finalQuantity = quantity;
        return handleCommandResult(GlobalMarketPlusAPI.buyItem(player, finalPrice, finalQuantity), "已发起收购: {} x{}，单价: ${}", new Object[]{displayName, finalQuantity, String.format("%.2f", finalPrice)}, "发起收购失败，可能没有权限或命令不可用", "发起收购");
    }

    private CompletableFuture<SkillResult> cancelListing(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        List<MarketItemDetail> myItems = GlobalMarketPlusAPI.getMyMerchandises(player);
        if (myItems.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.success(I18nService.tr("你当前没有在售商品")));
        }

        String uidStr = context.getEntity("uid");
        if (uidStr == null || uidStr.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(I18nService.tr("你当前在售以下商品，请告诉我要下架哪个（回复编号）：")).append("\n");
            for (int i = 0; i < myItems.size(); i++) {
                MarketItemDetail item = myItems.get(i);
                sb.append(String.format("%d. %s x%d - $%.2f (UID:%d)", i + 1, item.getDisplayName(), item.getAmount(), item.getPrice(), item.getUid())).append("\n");
            }
            return CompletableFuture.completedFuture(SkillResult.needInfo(sb.toString().trim()));
        }

        // 解析 UID
        long targetUid;
        try {
            int index = Integer.parseInt(uidStr);
            if (index >= 1 && index <= myItems.size()) {
                targetUid = myItems.get(index - 1).getUid();
            } else {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("编号超出范围，请输入 1-{} 之间的数字", myItems.size())));
            }
        } catch (NumberFormatException e) {
            try {
                targetUid = Long.parseLong(uidStr);
            } catch (NumberFormatException e2) {
                return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("无效的编号或 UID: {}", uidStr)));
            }
        }

        // 所有权校验
        boolean owned = false;
        String targetItemName = null;
        for (MarketItemDetail item : myItems) {
            if (item.getUid() == targetUid) {
                owned = true;
                targetItemName = item.getDisplayName();
                break;
            }
        }
        if (!owned) {
            return CompletableFuture.completedFuture(SkillResult.failure("该商品不属于你，无法下架"));
        }

        return handleCommandResult(GlobalMarketPlusAPI.cancelMerchandise(player, targetUid), "已成功下架商品: {}，物品已归还到你的邮箱", new Object[]{targetItemName}, "下架失败，商品可能已过期或被购买", "下架商品");
    }

    CompletableFuture<SkillResult> transferMoney(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        String targetPlayer = context.getEntity("target_player");
        if (targetPlayer == null || targetPlayer.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要转给谁？")));
        }

        if (targetPlayer.equalsIgnoreCase(player.getName())) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("不能向自己转账")));
        }

        String amountStr = context.getEntity("amount");
        if (amountStr == null || amountStr.isEmpty()) {
            double balance = GlobalMarketPlusAPI.getBalance(player);
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你当前余额 ${}，要转多少给 {}？", String.format("%.2f", balance), targetPlayer)));
        }

        double amount;
        try {
            amount = parseMoney(amountStr);
            if (amount <= 0) {
                return CompletableFuture.completedFuture(SkillResult.failure("转账金额必须大于 0"));
            }
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("金额格式不正确: {}", amountStr)));
        }

        double balance = GlobalMarketPlusAPI.getBalance(player);
        if (balance >= 0 && balance < amount) {
            // 确认后发现余额不足（漂移）→ 重新引导；否则沿用 failure
            if (context.isConfirmed()) {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你确认时要转 ${}，但当前余额仅 ${}（期间有变动）。要转多少？或说「取消」。", String.format("%.2f", amount), String.format("%.2f", balance))));
            }
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("余额不足，当前余额 ${}，需要 ${}", String.format("%.2f", balance), String.format("%.2f", amount))));
        }

        // 超过余额 50% 需确认（确认位由 isConfirmed 表达）
        if (balance > 0 && amount > balance * 0.5) {
            if (!context.isConfirmed()) {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("即将向 {} 转账 ${}，占你余额的较大比例。确认转账吗？", targetPlayer, String.format("%.2f", amount))));
            }
        }

        final double finalAmount = amount;
        return handleCommandResult(GlobalMarketPlusAPI.transferMoney(player, targetPlayer, finalAmount), "已成功向 {} 转账 ${}", new Object[]{targetPlayer, String.format("%.2f", finalAmount)}, "转账失败，可能目标玩家不存在或余额不足", "转账");
    }

    private CompletableFuture<SkillResult> auctionItem(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        ItemStack handItem = readHandItem(player);
        if (handItem == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("读取手持物品失败，请稍后重试"));
        }
        if (handItem.getType() == Material.AIR) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请先将需要拍卖的物品拿在主手中")));
        }

        String itemTypeName = handItem.getType().name();
        ItemTranslator translator = ItemTranslator.getInstance();
        String displayName = translator.translateToChinese(itemTypeName);
        int stackAmount = handItem.getAmount();

        // 解析起拍价
        Double price = parsePrice(context);
        if (price != null && price == Double.NEGATIVE_INFINITY) {
            return invalidPriceFailure(context.getEntity("price"));
        }

        // 解析数量
        int quantity = parseQuantity(context, stackAmount);
        if (quantity < 0) {
            return invalidQuantityFailure(quantity, context.getEntity("quantity"), stackAmount);
        }
        if (quantity == 0) quantity = stackAmount;

        // 起拍价缺失 → 引导补充
        if (price == null) {
            double refPrice = GlobalMarketPlusAPI.getItemReferencePrice(itemTypeName);
            if (refPrice > 0) {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你想以多少起拍价拍卖 {} x{}？市场参考价: ${}", displayName, quantity, String.format("%.2f", refPrice))));
            } else {
                return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你想以多少起拍价拍卖 {} x{}？市场暂无参考价", displayName, quantity)));
            }
        }

        // 余额预检查（拍卖需缴纳税额，余额为 0 时 GMP 会拒绝）
        double balance = GlobalMarketPlusAPI.getBalance(player);
        if (balance == 0) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("余额不足，发起拍卖需要缴纳税额，当前余额为 $0")));
        }

        final double finalPrice = price;
        final int finalQuantity = quantity;
        return handleCommandResult(GlobalMarketPlusAPI.auctionItem(player, finalPrice, finalQuantity), "已发起拍卖: {} x{}，起拍价: ${}", new Object[]{displayName, finalQuantity, String.format("%.2f", finalPrice)}, "发起拍卖失败，可能没有权限、余额不足以支付税额或命令不可用", "发起拍卖");
    }

    private CompletableFuture<SkillResult> sellInventory(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        // 解析价格
        Double price = parsePrice(context);
        if (price != null && price == Double.NEGATIVE_INFINITY) {
            return invalidPriceFailure(context.getEntity("price"));
        }

        // 价格缺失 → 引导补充
        if (price == null) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要以多少单价批量上架？")));
        }

        // 直接打开批量上架 GUI（玩家在 GUI 中自行放入物品并确认）
        final double finalPrice = price;
        return handleCommandResult(GlobalMarketPlusAPI.sellInventory(player, finalPrice), "已为你打开批量上架页面，单价: ${}，请在 GUI 中放入物品并确认", new Object[]{String.format("%.2f", finalPrice)}, "批量上架失败，可能没有权限或命令不可用", "批量上架");
    }

    private CompletableFuture<SkillResult> buyInventory(SkillContext context) {
        Player player = requireOnlinePlayer(context);
        if (player == null) return offlineFailure();

        // 解析价格
        Double price = parsePrice(context);
        if (price != null && price == Double.NEGATIVE_INFINITY) {
            return invalidPriceFailure(context.getEntity("price"));
        }

        // 价格缺失 → 引导补充
        if (price == null) {
            double balance = GlobalMarketPlusAPI.getBalance(player);
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要以多少单价批量收购？当前余额: ${}", String.format("%.2f", balance))));
        }

        // 余额预检查
        double balance = GlobalMarketPlusAPI.getBalance(player);
        if (balance >= 0 && balance < price) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("余额不足，当前余额 ${}", String.format("%.2f", balance))));
        }

        // 直接打开批量收购 GUI（玩家在 GUI 中自行放入物品并确认）
        final double finalPrice = price;
        return handleCommandResult(GlobalMarketPlusAPI.buyInventory(player, finalPrice), "已为你打开批量收购页面，单价: ${}，请在 GUI 中放入物品并确认", new Object[]{String.format("%.2f", finalPrice)}, "批量收购失败，可能没有权限或命令不可用", "批量收购");
    }

    /**
     * 价格引导
     */
    private CompletableFuture<SkillResult> buildPriceGuidance(String itemTypeName, String displayName, int quantity) {
        double refPrice = GlobalMarketPlusAPI.getItemReferencePrice(itemTypeName);

        if (refPrice > 0) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你手中的 {} x{}，当前市场最低价为 ${}。请告诉我你希望以多少钱的单价上架？", displayName, quantity, String.format("%.2f", refPrice))));
        } else {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("你手中的 {} x{}，市场暂无参考价。请告诉我你希望以多少钱的单价上架？", displayName, quantity)));
        }
    }
}
