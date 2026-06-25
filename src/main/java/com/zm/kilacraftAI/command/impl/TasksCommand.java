package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.config.LanguageManager;
import org.bukkit.command.CommandSender;

/**
 * /kila tasks：查看定时任务状态（tasks 权限）。
 */
public final class TasksCommand {

    private TasksCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.TASKS.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandTasksNoPermission());
            return;
        }

        var scheduler = plugin.getTaskScheduler();
        if (scheduler == null) {
            sender.sendMessage(lm.getCommandTasksNotInit());
            return;
        }

        for (String line : scheduler.getStatusSummary()) {
            sender.sendMessage(line);
        }
    }
}
