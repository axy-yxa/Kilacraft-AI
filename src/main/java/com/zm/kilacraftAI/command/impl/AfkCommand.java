package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.model.afktask.AFKTask;
import com.zm.kilacraftAI.service.afktask.AFKTaskManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * /kila afk [query|cancel]：查询/取消挂机任务（afk 权限，仅限玩家）。
 */
public final class AfkCommand {

    private AfkCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lm.getCommandAfkPlayerOnly());
            return;
        }

        AFKTaskManager manager = plugin.getAfkTaskManager();
        if (manager == null) {
            player.sendMessage(lm.getCommandAfkNotEnabled());
            return;
        }

        String subAction = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "query";

        switch (subAction) {
            case "cancel" -> {
                if (!manager.hasTask(player.getUniqueId())) {
                    player.sendMessage(lm.getCommandAfkNoTask());
                    return;
                }
                AFKTask task = manager.getTask(player.getUniqueId());
                manager.cancelTask(player.getUniqueId());
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkCancelled(), "desc", task.getTaskDescription()));
            }
            case "query", "" -> {
                if (!manager.hasTask(player.getUniqueId())) {
                    player.sendMessage(lm.getCommandAfkNoTask());
                    return;
                }
                AFKTask task = manager.getTask(player.getUniqueId());
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                player.sendMessage(lm.getCommandAfkCurrentTitle());
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkTaskId(), "id", task.getTaskId()));
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkTaskType(), "type", task.getTaskType().getLocalizedDescription()));
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkTaskDesc(), "desc", task.getTaskDescription()));
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkTaskStatus(), "status", task.getStatusText()));
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkTaskTime(), "time", sdf.format(new Date(task.getCreatedAt()))));
                player.sendMessage(lm.getCommandAfkCancelHint());
            }
            default -> {
                player.sendMessage(lm.replacePlaceholders(lm.getCommandAfkUnknownSub(), "cmd", subAction));
                player.sendMessage(lm.getCommandAfkUsage());
            }
        }
    }
}
