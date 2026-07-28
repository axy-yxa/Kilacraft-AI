package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.suggestion.SuggestionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /kila suggestion：对话推荐玩家级开关。
 *
 * <p>子命令：{@code on} / {@code off} / {@code status}（无参数默认显示 status）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public final class SuggestionCommand {

    private SuggestionCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c" + I18nService.tr("对话推荐仅玩家可用。"));
            return;
        }
        LanguageManager lm = plugin.getLanguageManager();
        SuggestionManager mgr = plugin.getSuggestionManager();
        if (mgr == null) {
            player.sendMessage(lm.getCommandSuggestionNotInit());
            return;
        }

        String sub = args.length > 1 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "on" -> handleOn(mgr, player, lm);
            case "off" -> handleOff(mgr, player, lm);
            case "status" -> handleStatus(mgr, player, lm);
            default -> player.sendMessage(lm.getCommandSuggestionUsage());
        }
    }

    private static void handleOn(SuggestionManager mgr, Player player, LanguageManager lm) {
        if (mgr.isSuggestionEnabled(player.getUniqueId())) {
            player.sendMessage(lm.getCommandSuggestionAlreadyOn());
            return;
        }
        mgr.enable(player.getUniqueId());
        player.sendMessage(lm.getCommandSuggestionOn());
    }

    private static void handleOff(SuggestionManager mgr, Player player, LanguageManager lm) {
        if (!mgr.isSuggestionEnabled(player.getUniqueId())) {
            player.sendMessage(lm.getCommandSuggestionAlreadyOff());
            return;
        }
        mgr.disable(player.getUniqueId());
        player.sendMessage(lm.getCommandSuggestionOff());
    }

    private static void handleStatus(SuggestionManager mgr, Player player, LanguageManager lm) {
        boolean on = mgr.isSuggestionEnabled(player.getUniqueId());
        player.sendMessage(on ? lm.getCommandSuggestionStatusOn() : lm.getCommandSuggestionStatusOff());
    }
}
