package com.zm.kilacraftAI.service.event;

import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import studio.trc.bukkit.globalmarketplus.api.event.MerchandiseSellEvent;
import studio.trc.bukkit.globalmarketplus.api.event.TransactionResultEvent;
import studio.trc.bukkit.globalmarketplus.merchandise.TransactionResultType;

import java.util.UUID;

/**
 * GlobalMarketPlus 事件采集器
 *
 * <p>监听 GMP 官方事件，写入 kca_server_event 表。
 * 与 EventCollector 职责分离，不侵入 Skill 代码。</p>
 *
 * <p>事件关联说明：市场事件以卖家视角采集（卖家离线时才能看到
 * "你的商品已售出"和"收到钱款"的通知）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-27
 */
public class MarketEventCollector implements Listener {

    private final DatabaseManager databaseManager;
    private volatile ServerEventDao eventDao;
    /**
     * 当前服务器标识（群组服区分）
     */
    private volatile String serverId;

    public MarketEventCollector(DatabaseManager databaseManager, String serverId) {
        this.databaseManager = databaseManager;
        this.eventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.serverId = serverId != null ? serverId : "";
    }

    /**
     * 热重载配置
     *
     * @param serverId 新的 server_id 值
     */
    public void refreshConfig(String serverId) {
        this.serverId = serverId != null ? serverId : "";
        this.eventDao = new ServerEventDao(databaseManager.getTablePrefix());
    }

    /**
     * 上架出售商品 → MARKET_ITEM_LISTED
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerchandiseSell(MerchandiseSellEvent event) {
        submitEvent(ServerEventTypeEnum.MARKET_ITEM_LISTED, event.getPlayer().getUniqueId(), buildListingData(event));
    }

    /**
     * 交易完成后 → MARKET_ITEM_SOLD + MARKET_MONEY_RECEIVED（均关联卖家）
     *
     * <p>使用 TransactionResultEvent（交易确认后触发），从 TransactionResult 获取真实的价格和数量。</p>
     * <p>只记录成功的交易（resultType == SUCCESSFUL），过滤掉余额不足、过期、不存在等失败情况。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransactionResult(TransactionResultEvent event) {
        var result = event.getResult();

        // 只记录成功的交易
        if (result.getResultType() != TransactionResultType.SUCCESSFUL) {
            return;
        }

        var merchandise = event.getMerchandise();
        UUID sellerUuid = merchandise.getOwnerUUID();
        String itemDesc = merchandise.getItem().getType().name() + " x" + result.getAmount();
        String priceDesc = String.format("%.1f", result.getPrice());

        // 商品售出（卖家视角）
        submitEvent(ServerEventTypeEnum.MARKET_ITEM_SOLD, sellerUuid, itemDesc);

        // 收到钱款（卖家视角）
        submitEvent(ServerEventTypeEnum.MARKET_MONEY_RECEIVED, sellerUuid, priceDesc);
    }

    private void submitEvent(ServerEventTypeEnum type, UUID playerUuid, String data) {
        final String currentServerId = this.serverId;
        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                eventDao.insert(conn, ServerEvent.of(type, playerUuid, data), currentServerId);
            } catch (Exception e) {
                PluginLoggerUtil.error("市场事件", "写入市场事件失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 构建上架商品的数据描述（格式："DIAMOND_SWORD x64 价格100.0"）
     */
    private String buildListingData(MerchandiseSellEvent event) {
        String itemName = event.getItem().getType().name();
        int amount = event.getAmount();
        String price = String.format("%.1f", event.getPrice());
        return I18nService.tr("{} x{} 价格{}", itemName, amount, price);
    }
}
