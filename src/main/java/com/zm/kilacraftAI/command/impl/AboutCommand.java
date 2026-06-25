package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.service.update.UpdateChecker;
import org.bukkit.command.CommandSender;

/**
 * /kila about：版本与更新检查（管理员专用，admin.info）。
 * 复用 UpdateChecker 的发布源检测（按 i18n 语言选 GitHub/Gitee）。
 * 网络请求在 IO 线程池执行，结果回主线程。
 */
public final class AboutCommand {

    private AboutCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.ADMIN_INFO.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandAboutNoPermission());
            return;
        }

        String current = plugin.getDescription().getVersion();
        sender.sendMessage(lm.getCommandAboutTitle());
        sender.sendMessage(lm.replacePlaceholders(lm.getCommandAboutCurrent(), "ver", current));
        sender.sendMessage(lm.getCommandAboutChecking());

        UpdateChecker checker = new UpdateChecker(plugin);
        FoliaCompat.getIOPool().execute(() -> {
            try {
                UpdateChecker.ReleaseInfo latest = checker.fetchLatestRelease();
                if (latest == null) {
                    FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandAboutCheckFailed()));
                    return;
                }
                boolean hasUpdate = UpdateChecker.isNewerVersion(current, latest.tagName());
                FoliaCompat.runTask(plugin, () -> {
                    // 始终显示最新 release 标签和版本说明（name 即 tag 发布时填的 title）
                    sender.sendMessage(lm.replacePlaceholders(lm.getCommandAboutLatest(), "ver", latest.tagName()));
                    if (!latest.name().isEmpty() && !latest.name().equals(latest.tagName())) {
                        sender.sendMessage(lm.replacePlaceholders(lm.getCommandAboutReleaseNotes(), "notes", latest.name()));
                    }
                    if (hasUpdate) {
                        sender.sendMessage(lm.getCommandAboutNewVersion());
                    } else {
                        sender.sendMessage(lm.getCommandAboutUpToDate());
                    }
                    if (!latest.publishedAt().isEmpty()) {
                        sender.sendMessage(lm.replacePlaceholders(lm.getCommandAboutPublished(), "date", latest.publishedAt()));
                    }
                    if (!latest.htmlUrl().isEmpty()) {
                        sender.sendMessage(lm.replacePlaceholders(lm.getCommandAboutDownload(), "url", latest.htmlUrl()));
                    }
                });
            } catch (Exception e) {
                FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandAboutCheckFailed()));
            }
        });
    }
}
