package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.util.AIRequestValidatorUtil;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.PluginCommandResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * /kila plugins：第三方插件集成（控制台专用）。
 */
public final class PluginsCommand {

    private PluginsCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        AIRequestValidatorUtil validator = new AIRequestValidatorUtil(plugin);

        if (sender instanceof Player player) {
            player.sendMessage(lm.getPluginCommandPlayerBlocked());
            PluginLoggerUtil.warn("命令", lm.replacePlaceholders(lm.getLogPlayerCommandAttempt(), "player", player.getName()));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(lm.getPluginCommandInsufficientArgs());
            sender.sendMessage(lm.getPluginCommandUsageExample());
            sender.sendMessage(lm.getPluginCommandCallbackHint());
            sender.sendMessage(lm.getPluginCommandCallbackExample());
            sender.sendMessage(lm.getPluginCommandCallbackPlaceholderHint());
            return;
        }

        String personality = args[1];
        String message = args[2];
        String uuidString = args[3];
        String callbackCommand = null;
        if (args.length >= 5) {
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (i > 4) sb.append(" ");
                sb.append(args[i]);
            }
            callbackCommand = sb.toString();
        }
        final String finalCallbackCommand = callbackCommand;

        UUID targetPlayerId;
        try {
            targetPlayerId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(lm.getPluginCommandInvalidUuid() + uuidString);
            sender.sendMessage(lm.getPluginCommandUuidFormatHint());
            return;
        }

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerId);
        String targetPlayerName;
        if (targetPlayer != null) {
            targetPlayerName = targetPlayer.getName();
        } else {
            String offlineName = plugin.getServer().getOfflinePlayer(targetPlayerId).getName();
            targetPlayerName = offlineName != null ? offlineName : "Unknown";
        }

        PersonalitiesConfigManager personalitiesConfig = plugin.getPersonalitiesConfigManager();
        if (!personalitiesConfig.hasPersonality(personality)) {
            sender.sendMessage(lm.getPluginCommandPersonalityNotFound() + personality);
            sender.sendMessage(lm.getPluginCommandPersonalityListHint() + String.join(", ", personalitiesConfig.getAllPersonalities()));
            return;
        }

        if (targetPlayer != null) {
            if (!validator.canUseAIInWorld(targetPlayer)) {
                sender.sendMessage(lm.replacePlaceholders(lm.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
                return;
            }
        }

        if (!validator.isPluginCommandCooldownReady(targetPlayerId)) {
            long remainingSeconds = validator.getPluginCommandRemainingCooldownSeconds(targetPlayerId);
            if (remainingSeconds > 0) {
                sender.sendMessage(lm.replacePlaceholders(lm.getPluginCommandCooldownWarning(), "player", targetPlayerName, "seconds", String.valueOf(remainingSeconds)));
            }
            return;
        }

        String historyKey = validator.getPluginCommandHistoryKey(targetPlayerId, personality);
        Deque<ConversationManager.Message> pluginHistory = plugin.getConversationManager().getOrCreatePluginHistory(historyKey);
        validator.startCooldown(targetPlayerId);

        AIResponseHandler handler = new PluginCommandResponseHandler(sender, targetPlayerId, targetPlayerName);
        final String finalPersonality = personality;
        // {player} 占位符替换由 Provider 咽喉统一处理，传含占位符的原始模板
        String personalityPrompt = personalitiesConfig.getPersonalityPrompt(personality);

        PluginLoggerUtil.debug("命令", "插件命令请求 - 人格：{}, 玩家：{}, UUID: {}", personality, targetPlayerName, targetPlayerId);
        PluginLoggerUtil.debug("命令", "人格提示词：{}", personalityPrompt);

        ConversationPersistenceService persistenceService = plugin.getPersistenceService();
        if (persistenceService != null) {
            persistenceService.loadHistoryIfNeeded(targetPlayerId, personality, loadedHistory -> {
                if (!loadedHistory.isEmpty() && pluginHistory.isEmpty()) {
                    for (ConversationManager.Message msg : loadedHistory) {
                        pluginHistory.addLast(msg);
                    }
                }
                PluginLoggerUtil.debug("命令", "历史记录数量：{}", pluginHistory.size());
                processLLM(plugin, lm, validator, message, targetPlayerName, targetPlayer, targetPlayerId, pluginHistory, handler, personalityPrompt, finalPersonality, finalCallbackCommand, sender);
            }, ConversationSourceEnum.PLUGIN);
        } else {
            PluginLoggerUtil.debug("命令", "历史记录数量：{}", pluginHistory.size());
            processLLM(plugin, lm, validator, message, targetPlayerName, targetPlayer, targetPlayerId, pluginHistory, handler, personalityPrompt, finalPersonality, finalCallbackCommand, sender);
        }
    }

    private static void processLLM(KilacraftAI plugin, LanguageManager lm, AIRequestValidatorUtil validator, String message, String targetPlayerName, Player targetPlayer, UUID targetPlayerId, Deque<ConversationManager.Message> pluginHistory, AIResponseHandler handler, String personalityPrompt, String finalPersonality, String finalCallbackCommand, CommandSender sender) {
        // 全局预算预检：被动调用在熔断窗口内被拒，回调不执行。
        // 第三方插件代玩家发起的调用，玩家整体调用过多时应降级，避免打爆外部 LLM 配额。
        if (plugin.getLlmOutputCoordinator() != null) {
            LLMBudgetManager budget = plugin.getLlmOutputCoordinator().getBudgetManager();
            if (!budget.tryAcquire(targetPlayerId, LLMBudgetManager.Priority.PASSIVE)) {
                PluginLoggerUtil.warn("命令", I18nService.tr("LLM 预算熔断，跳过插件命令调用（玩家 {}）", targetPlayerName));
                return;
            }
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(message, targetPlayer, pluginHistory, handler, personalityPrompt, true, true, false, CacheCallTypeEnum.NORMAL_CHAT).thenAccept(fullResponse -> {
            if (LLMResponseUtil.isErrorResponse(fullResponse)) return;

            validator.saveToHistory(pluginHistory, message, fullResponse, targetPlayerId, finalPersonality);

            if (finalCallbackCommand != null && !finalCallbackCommand.isEmpty()) {
                int timeoutSeconds = plugin.getConfigManager().getCallbackTimeoutSeconds();
                long startTime = System.currentTimeMillis();
                FutureTask<Void> task = new FutureTask<>(() -> {
                    executeCallback(finalCallbackCommand, fullResponse);
                    return null;
                });
                FoliaCompat.runTask(plugin, task);
                try {
                    task.get(timeoutSeconds, TimeUnit.SECONDS);
                    long elapsed = System.currentTimeMillis() - startTime;
                    PluginLoggerUtil.debug("命令", String.format("回调执行耗时: %dms", elapsed));
                    if (elapsed > timeoutSeconds * 800L) {
                        PluginLoggerUtil.warn("命令", String.format("回调命令执行较慢 (%dms)，接近超时阈值 (%ds)", elapsed, timeoutSeconds));
                    }
                } catch (TimeoutException e) {
                    task.cancel(true);
                    PluginLoggerUtil.warn("命令", String.format("回调命令执行超时 (%ds)，已强制中断。命令: %s", timeoutSeconds, finalCallbackCommand.length() > 100 ? finalCallbackCommand.substring(0, 100) + "..." : finalCallbackCommand));
                } catch (CancellationException e) {
                    // cancelled by timeout, already logged
                } catch (ExecutionException e) {
                    PluginLoggerUtil.warn("命令", I18nService.tr("回调命令执行失败: {}", e.getCause().getMessage()));
                    PluginLoggerUtil.debug("命令", "原始命令: {}", finalCallbackCommand);
                } catch (InterruptedException e) {
                    PluginLoggerUtil.warn("命令", I18nService.tr("回调命令执行被中断: {}", e.getMessage()), e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    PluginLoggerUtil.warn("命令", I18nService.tr("回调命令执行异常: {}", e.getMessage()), e);
                } finally {
                    plugin.getConversationManager().pollLatestAIResponse(targetPlayerId, finalPersonality);
                    PluginLoggerUtil.debug("命令", "回调命令执行完毕，缓存已删除");
                }
            } else {
                PluginLoggerUtil.debug("命令", "保留缓存供轮询获取");
            }
            PluginLoggerUtil.debug("命令", "插件命令响应完成，响应长度：{}", fullResponse.length());
        }).exceptionally(throwable -> {
            sender.sendMessage(lm.getPluginCommandError() + throwable.getMessage());
            PluginLoggerUtil.error("命令", lm.getLogPluginCommandAiError() + throwable.getMessage(), throwable);
            return null;
        });
    }

    private static void executeCallback(String callbackCommand, String response) {
        // {response} 来自 LLM 输出（不可信），注入控制台命令前需净化：过滤换行 + 转义双引号
        String safeResponse = response.replaceAll("[\\r\\n]", " ").replace("\"", "\\\"");
        String finalCommand = callbackCommand.replace("{response}", safeResponse);
        PluginLoggerUtil.debug("命令", "执行回调命令: {}", finalCommand);
        FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
    }
}
