package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.service.notification.NotificationService;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * /kila notify test：外部通知渠道测试（admin.health 权限）。
 */
public final class NotifyCommand {

    private NotifyCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.ADMIN_HEALTH.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandNotifyNoPermission());
            return;
        }

        String subAction = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";

        if (!"test".equals(subAction)) {
            sender.sendMessage(lm.getCommandNotifyUsage());
            return;
        }

        NotificationService notificationService = plugin.getNotificationService();
        if (notificationService == null || !notificationService.isReady()) {
            sender.sendMessage(lm.getCommandNotifyNotReady());
            return;
        }

        sender.sendMessage(lm.replacePlaceholders(lm.getCommandNotifyTesting(), "count", String.valueOf(notificationService.getChannelCount())));

        FoliaCompat.getIOPool().execute(() -> {
            List<NotificationService.ChannelTestResult> results = notificationService.testAllChannels();
            FoliaCompat.runTask(plugin, () -> {
                sender.sendMessage(lm.getCommandNotifyResultTitle());
                for (NotificationService.ChannelTestResult result : results) {
                    String status = result.result().success() ? "§a" + lm.getCommandNotifySendSuccess() : lm.replacePlaceholders(lm.getCommandNotifySendFailed(), "msg", result.result().message());
                    sender.sendMessage("§7[" + result.type() + "] " + status);
                }
            });
        });

    }
}
