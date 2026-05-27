package com.zm.kilacraftAI.service.profile;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.service.event.EventCollector;
import com.zm.kilacraftAI.model.event.ServerEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 玩家画像事件采集器
 *
 * <p>监听玩家登录/登出事件，驱动 ProfileManager 和 EventCollector。</p>
 *
 * @author Zm_Mmm
 */
public class ProfileEventCollector implements Listener {

    private final KilacraftAI plugin;
    private final ProfileManager profileManager;
    private final EventCollector eventCollector;

    public ProfileEventCollector(KilacraftAI plugin, ProfileManager profileManager, EventCollector eventCollector) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.eventCollector = eventCollector;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // 异步加载画像（ProfileManager 内部处理首次登录判断 + 事件提交）
        profileManager.onPlayerJoin(uuid, name);

        // 仅提交普通登录事件
        eventCollector.submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_LOGIN, uuid, name));

        // 登录时检查是否需要画像分析
        if (plugin.getProfileAnalysisService() != null) {
            plugin.getProfileAnalysisService().tryAnalyze(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        var loc = player.getLocation();
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // 记录登出事件
        eventCollector.submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_LOGOUT, uuid, player.getName()));

        // 画像登出处理（含坐标）
        profileManager.onPlayerQuit(uuid, world, x, y, z);

        // 触发画像分析（异步，不阻塞登出流程）
        if (plugin.getProfileAnalysisService() != null) {
            plugin.getProfileAnalysisService().tryAnalyze(uuid);
        }
    }
}
