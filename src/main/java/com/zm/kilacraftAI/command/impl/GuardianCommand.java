package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.Guardian;
import com.zm.kilacraftAI.service.guardian.GuardianManager;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * /kila guardian：守护系统开关与查询。
 *
 * <p>子命令：
 * <ul>
 *   <li>{@code on} — 启用默认套餐</li>
 *   <li>{@code off} — 停用守护</li>
 *   <li>{@code status} — 查看当前状态（含各 monitor 的人类可读名）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class GuardianCommand {

    private GuardianCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c" + I18nService.tr("守护系统仅玩家可用。"));
            return;
        }
        LanguageManager lm = plugin.getLanguageManager();
        GuardianManager mgr = plugin.getGuardianManager();
        if (mgr == null) {
            player.sendMessage(lm.getCommandGuardianNotInit());
            return;
        }

        String sub = args.length > 1 ? args[1].toLowerCase() : "status";
        if (!PluginPermissionEnum.GUARDIAN.hasPermission(sender)) {
            player.sendMessage(lm.getCommandGuardianNoPermission());
            return;
        }
        switch (sub) {
            case "on" -> handleOn(plugin, mgr, player, lm);
            case "off" -> handleOff(mgr, player, lm);
            case "status" -> handleStatus(mgr, player, lm);
            default -> sendUsage(player, lm);
        }
    }

    private static void handleOn(KilacraftAI plugin, GuardianManager mgr, Player player, LanguageManager lm) {
        if (plugin.getGuardianConfigManager() == null || !plugin.getGuardianConfigManager().isEnabled()) {
            player.sendMessage(lm.getCommandGuardianDisabledGlobal());
            return;
        }
        if (mgr.isGuardianEnabled(player.getUniqueId())) {
            player.sendMessage(lm.getCommandGuardianAlreadyOn());
            return;
        }
        Optional<List<String>> result = mgr.enable(player);
        if (result.isPresent()) {
            int n = result.get().size();
            player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianOnSuccess(), "n", String.valueOf(n)));
        } else {
            player.sendMessage(lm.getCommandGuardianDisabledGlobal());
        }
    }

    private static void handleOff(GuardianManager mgr, Player player, LanguageManager lm) {
        if (!mgr.isGuardianEnabled(player.getUniqueId())) {
            player.sendMessage(lm.getCommandGuardianAlreadyOff());
            return;
        }
        mgr.disable(player.getUniqueId());
        player.sendMessage(lm.getCommandGuardianOff());
    }

    private static void handleStatus(GuardianManager mgr, Player player, LanguageManager lm) {
        boolean on = mgr.isGuardianEnabled(player.getUniqueId());
        Guardian g = mgr.getGuardian(player.getUniqueId());
        int monitorCount = (g != null) ? g.monitors().size() : 0;
        player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianStatus(), "state", on ? lm.getCommandGuardianStateOn() : lm.getCommandGuardianStateOff(), "n", String.valueOf(monitorCount)));
        if (!on) {
            return;
        }
        if (g != null && !g.monitors().isEmpty()) {
            for (Monitor m : g.monitors()) {
                player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianMonitorLine(), "name", m.displayName()));
            }
        }
    }

    private static void sendUsage(Player player, LanguageManager lm) {
        player.sendMessage(lm.getCommandGuardianUsage());
    }
}
