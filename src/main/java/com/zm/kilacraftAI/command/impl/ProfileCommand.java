package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.service.health.ManualSession;
import com.zm.kilacraftAI.service.health.ServerHealthGuardian;
import com.zm.kilacraftAI.service.health.SparkOutputCapture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * /kila profile start|stop|status：手动性能采样（admin.health 权限）。
 */
public final class ProfileCommand {

    private ProfileCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.ADMIN_HEALTH.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandProfileNoPermission());
            return;
        }

        ServerHealthGuardian guardian = plugin.getServerHealthGuardian();
        if (guardian == null) {
            var adminConfig = plugin.getAdminConfigManager();
            if (!adminConfig.isThinkingModelConfigured()) {
                sender.sendMessage(lm.getCommandProfileNoModel());
            } else if (!adminConfig.isGuardianEnabled()) {
                sender.sendMessage(lm.getCommandProfileGuardianDisabled());
            } else {
                sender.sendMessage(lm.getCommandProfileNoSpark());
            }
            return;
        }

        ManualSession session = guardian.getManualSession();
        String subAction = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";

        switch (subAction) {
            case "start" -> {
                int duration = 60;
                if (args.length >= 3) {
                    try {
                        duration = Integer.parseInt(args[2]);
                        duration = Math.max(30, Math.min(120, duration));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(lm.getCommandProfileInvalidDuration());
                        return;
                    }
                }

                String playerName = sender instanceof Player p ? p.getName() : "Console";
                if (session.tryStart(playerName, duration)) {
                    session.setActivityBefore(guardian.captureActivitySnapshot());
                    SparkOutputCapture capture = new SparkOutputCapture();
                    capture.startCapture();
                    FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler start --timeout " + duration);
                    sender.sendMessage(lm.replacePlaceholders(lm.getCommandProfileStarted(), "seconds", String.valueOf(duration)));
                    sender.sendMessage(lm.getCommandProfileWillReport());

                    final int captureTimeout = duration + 30;
                    final String finalPlayerName = playerName;
                    FoliaCompat.getIOPool().execute(() -> {
                        try {
                            String url = capture.awaitUrl(captureTimeout, TimeUnit.SECONDS);
                            capture.stopCapture();
                            if (url != null && session.isRunning() && finalPlayerName.equals(session.getOperatorName())) {
                                session.setProfilerUrl(url);
                                guardian.startManualAnalysis(finalPlayerName);
                            } else if (url == null) {
                                PluginLoggerUtil.warn("健康监控", "手动采样 Profiler URL 捕获超时（{}秒）", captureTimeout);
                                String localPath = capture.getLocalFilePath();
                                if (localPath != null && session.isRunning() && finalPlayerName.equals(session.getOperatorName())) {
                                    PluginLoggerUtil.info("健康监控", "回退到 Spark 本地文件: {}", localPath);
                                    guardian.startManualAnalysisWithLocalFile(finalPlayerName, localPath);
                                } else {
                                    session.reset();
                                    FoliaCompat.runTask(plugin, () -> {
                                        if (!"Console".equals(finalPlayerName)) {
                                            Player operator = Bukkit.getPlayer(finalPlayerName);
                                            if (operator != null) {
                                                operator.sendMessage(MessageUtil.getAIPrefix() + lm.getCommandProfileUploadFailed());
                                                operator.sendMessage(MessageUtil.getAIPrefix() + lm.getCommandProfileUploadFailReason());
                                            }
                                        }
                                    });
                                }
                            }
                        } finally {
                            capture.stopCapture();
                        }
                    });
                } else {
                    sender.sendMessage(lm.getCommandProfileAlreadyRunning());
                }
            }
            case "stop" -> {
                if (!session.isRunning()) {
                    sender.sendMessage(lm.getCommandProfileNoRunning());
                    return;
                }
                FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler stop");
                session.reset();
                sender.sendMessage(lm.getCommandProfileStopped());
            }
            case "status" -> {
                ManualSession.Status status = session.getStatus();
                sender.sendMessage(lm.replacePlaceholders(lm.getCommandProfileStatus(), "status", String.valueOf(status)));
                if (status == ManualSession.Status.RUNNING) {
                    sender.sendMessage(lm.replacePlaceholders(lm.getCommandProfileOperatorInfo(), "name", session.getOperatorName(), "seconds", String.valueOf(session.getDurationSeconds())));
                }
                if (guardian.isAnalyzing()) {
                    sender.sendMessage(lm.getCommandProfileAnalyzing());
                }
            }
            default -> {
                sender.sendMessage(lm.replacePlaceholders(lm.getCommandProfileUnknownSub(), "cmd", subAction));
                sender.sendMessage(lm.getCommandProfileUsage());
            }
        }
    }
}
