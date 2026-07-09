package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.service.guardian.AlertCategory;
import com.zm.kilacraftAI.service.guardian.GuardianManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * /kila guardian：守护系统开关与反馈。
 *
 * <p>子命令：
 * <ul>
 *   <li>{@code on} — 启用默认套餐</li>
 *   <li>{@code off} — 停用守护</li>
 *   <li>{@code status} — 查看当前状态</li>
 *   <li>{@code silence <分类>} — 静音分类</li>
 *   <li>{@code unsilence <分类>} — 取消静音</li>
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
            sender.sendMessage("§c守护系统仅玩家可用。");
            return;
        }
        LanguageManager lm = plugin.getLanguageManager();
        GuardianManager mgr = plugin.getGuardianManager();
        if (mgr == null) {
            sender.sendMessage(lm.getCommandGuardianNotInit());
            return;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        // 写入操作（开启/静音）需权限；退出/查询不需权限——防权限撤销后玩家无法关闭自己的守护
        if (("on".equals(sub) || "silence".equals(sub) || "unsilence".equals(sub))
                && !PluginPermissionEnum.GUARDIAN.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandGuardianNoPermission());
            return;
        }
        switch (sub) {
            case "on" -> handleOn(plugin, mgr, player, lm);
            case "off" -> handleOff(mgr, player, lm);
            case "status" -> handleStatus(mgr, player, lm);
            case "silence" -> handleSilence(mgr, player, args, lm, true);
            case "unsilence" -> handleSilence(mgr, player, args, lm, false);
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
        player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianStatus(),
                "state", on ? lm.getCommandGuardianStateOn() : lm.getCommandGuardianStateOff(),
                "n", String.valueOf(mgr.activeCount())));
        java.util.Set<AlertCategory> silenced = mgr.getSilencedCategories(player.getUniqueId());
        if (!silenced.isEmpty()) {
            player.sendMessage("§7  已静音: §c" + silenced.stream()
                    .map(AlertCategory::name).reduce((a, b) -> a + "§7, §c" + b).orElse(""));
        }
    }

    private static void handleSilence(GuardianManager mgr, Player player, String[] args, LanguageManager lm, boolean silence) {
        if (!mgr.isGuardianEnabled(player.getUniqueId())) {
            player.sendMessage(lm.getCommandGuardianAlreadyOff());
            return;
        }
        if (args.length < 2) {
            player.sendMessage(lm.getCommandGuardianSilenceUsage());
            return;
        }
        try {
            AlertCategory cat = AlertCategory.valueOf(args[1].toUpperCase());
            if (silence) {
                mgr.silence(player.getUniqueId(), cat);
                player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianSilenced(), "cat", args[1].toUpperCase()));
            } else {
                mgr.unsilence(player.getUniqueId(), cat);
                player.sendMessage(lm.replacePlaceholders(lm.getCommandGuardianUnsilenced(), "cat", args[1].toUpperCase()));
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(lm.getCommandGuardianInvalidCategory());
        }
    }

    private static void sendUsage(Player player, LanguageManager lm) {
        player.sendMessage(lm.getCommandGuardianUsage());
    }
}
