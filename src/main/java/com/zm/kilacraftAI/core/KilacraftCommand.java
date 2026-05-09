package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.db.ConversationPersistenceService;
import com.zm.kilacraftAI.db.ConversationSource;
import com.zm.kilacraftAI.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.PluginCommandResponseHandler;
import com.zm.kilacraftAI.knowledge.EmbeddingService;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.afktask.AFKTask;
import com.zm.kilacraftAI.skills.afktask.AFKTaskManager;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 命令处理
 *
 * @author Zm_Mmm
 * @since 2026-03-24
 */
public class KilacraftCommand implements CommandExecutor {

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;
    private final LanguageManager languageManager;
    private final AIRequestHandler aiRequestHandler;

    public KilacraftCommand(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
        this.aiRequestHandler = new AIRequestHandler(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // 缓存配置管理器引用
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length == 0) {
            sendHelpMessage(sender, configManager.isEnableChatCommand());
            return true;
        }

        // 子命令处理
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "reload" -> handleReloadCommand(sender);
            case "clear" -> handleClearCommand(sender, args);
            case "chat" -> handleChatCommand(sender, configManager);
            case "knowledge" -> handleKnowledgeCommand(sender, args);
            case "plugins" -> handlePluginsCommand(sender, args);
            case "personalities" -> handlePersonalitiesCommand(sender, args);
            case "afk" -> handleAfkCommand(sender, args);
            case "tasks" -> handleTasksCommand(sender);
            default -> handleNormalMessageCommand(sender, args);
        };
    }

    /**
     * 发送帮助消息
     */
    private void sendHelpMessage(CommandSender sender, boolean chatCommandEnabled) {
        // 发送基础帮助消息
        for (int i = 0; i < languageManager.getHelpMessages().size(); i++) {
            // 跳过 chat 命令提示如果功能被禁用
            if (i == 2 && !chatCommandEnabled) {
                continue;
            }
            sender.sendMessage(languageManager.getHelpMessages().get(i));
        }

        // 根据权限显示清除历史提示
        if (PluginPermissionEnum.CLEAR_SELF.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpClearSelf());
        }
        if (PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpClearOther());
        }

        // 根据权限显示挂机任务提示
        if (PluginPermissionEnum.AFK.hasPermission(sender)) {
            sender.sendMessage(languageManager.getHelpAfk());
            sender.sendMessage(languageManager.getHelpAfkSubcommands());
        }
    }

    /**
     * 处理 reload 命令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!PluginPermissionEnum.RELOAD.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionReload());
            return true;
        }

        try {
            plugin.getConfigManager().loadConfig();
            plugin.getI18nService().reload();
            plugin.getLanguageManager().loadConfig();

            // 热重载技能配置（包括 Bukkit API 元数据）
            if (plugin.getSkillConfigManager() != null) {
                plugin.getSkillConfigManager().reloadAllConfigs();
            }

            // 同步条件技能的注册状态
            plugin.syncConditionalSkills();

            // 热重载意图识别提示词配置
            if (plugin.getIntentPromptConfigManager() != null) {
                plugin.getIntentPromptConfigManager().reload();
            }

            // 热重载 AI 回复音效配置
            if (plugin.getSoundEffectManager() != null) {
                plugin.getSoundEffectManager().loadConfig();
            }

            // 热重载人格配置（语言变更后切换到对应语言的人格文件）
            if (plugin.getPersonalitiesConfigManager() != null) {
                plugin.getPersonalitiesConfigManager().reload();
            }

            // 热重载数据库配置（支持切换数据库类型，失败时自动回退旧连接池）
            if (plugin.getDatabaseConfigManager() != null && plugin.getDatabaseManager() != null) {
                try {
                    plugin.getDatabaseConfigManager().reload();
                    plugin.getDatabaseManager().reload(plugin.getDatabaseConfigManager().getConfig());
                    // 数据库切换成功后，将在线玩家画像补录到新库，避免退服时 UPDATE 丢失
                    if (plugin.getProfileManager() != null) {
                        plugin.getProfileManager().reconcileOnlineProfiles();
                    }
                } catch (Exception dbEx) {
                    PluginLogger.error("热重载", "数据库热重载失败，已回退到旧配置: {}", dbEx.getMessage());
                }
            }

            // 刷新知识检索器的算法参数（分段大小、BM25、阈值等）
            if (plugin.getKnowledgeRetriever() != null) {
                ConfigManager cm = plugin.getConfigManager();
                plugin.getKnowledgeRetriever().refreshConfig(cm.getMaxRelevantChunks(), cm.getMinRelevanceScore(), cm.getKnowledgeMaxChunkSize(), cm.getKnowledgeMinChunkSize(), cm.getKnowledgeChunkOverlap(), cm.getKeywordTopK(), cm.getBm25K1(), cm.getBm25B());
            }

            // 刷新 Embedding 阈值配置（仅 min_similarity，可热重载）
            // 注意：api_url/api_key/model/dimensions 等核心配置修改后必须重启服务器
            // 因为这些配置会影响向量计算，需要在启动时通过 precomputeAllChunks() 重新计算
            if (plugin.getEmbeddingService() != null && plugin.getConfigManager().isEmbeddingEnabled()) {
                plugin.getKnowledgeRetriever().setEmbeddingService(plugin.getEmbeddingService(), true, plugin.getConfigManager().getEmbeddingMinSimilarity());
            }

            // 热重载文本处理器（语言变更后需要重建分词器）
            com.zm.kilacraftAI.util.TextProcessorFactory.reset();
            // 重新初始化分词词典（按新语言加载对应词典）
            if (plugin.getConfigManager().isCustomDictionaryEnabled()) {
                com.zm.kilacraftAI.util.TextProcessorFactory.initialize(plugin.getConfigManager().getAllDictionaryWords());
            }

            sender.sendMessage(languageManager.getCommandReloadSuccess());
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogConfigReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandReloadFailure() + e.getMessage());
            PluginLogger.error("命令", languageManager.getLogAiRequestError() + e.getMessage(), e);
        }
        return true;
    }

    /**
     * 处理 clear 命令
     *
     * <p>支持清除自己或指定玩家的历史记录</p>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handleClearCommand(CommandSender sender, String[] args) {
        // 如果提供了玩家名称参数，清除指定玩家的历史（需要 kilacraft.clear.other 权限）
        if (args.length >= 2) {
            if (!PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
                sender.sendMessage(languageManager.getPermissionClearOther());
                return true;
            }

            String targetPlayerName = args[1];
            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
            UUID targetPlayerId;

            if (targetPlayer != null) {
                // 玩家在线，使用其 UUID
                targetPlayerId = targetPlayer.getUniqueId();
                targetPlayerName = targetPlayer.getName();
            } else {
                // 玩家不在线，尝试从离线数据获取 UUID
                targetPlayerId = plugin.getServer().getOfflinePlayer(targetPlayerName).getUniqueId();
            }

            // 清除该玩家的所有历史记录（包括普通和插件命令）
            plugin.getConversationManager().clearAllHistory(targetPlayerId);
            sender.sendMessage(languageManager.replacePlaceholders(languageManager.getCommandClearOtherSuccess(), "player", targetPlayerName));
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogClearOtherLogged(), "sender", getSenderName(sender), "player", targetPlayerName));
            return true;
        }

        // 没有参数，清除自己的历史记录（需要 kilacraft.clear.self 权限）
        if (!PluginPermissionEnum.CLEAR_SELF.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionClearSelf());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.getCommandClearConsoleHint());
            return true;
        }

        UUID playerId = player.getUniqueId();
        // 清除所有历史记录（包括普通和插件命令）
        plugin.getConversationManager().clearAllHistory(playerId);
        player.sendMessage(languageManager.getCommandClearSelfSuccess());
        PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogClearSelfLogged(), "player", player.getName()));
        return true;
    }

    /**
     * 处理 chat 命令
     */
    private boolean handleChatCommand(CommandSender sender, ConfigManager configManager) {
        if (!configManager.isEnableChatCommand()) {
            sender.sendMessage(languageManager.getFeatureChatModeDisabled());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.getFeatureChatModePlayerOnly());
            return true;
        }

        // 切换对话模式状态
        UUID playerId = player.getUniqueId();
        ConversationManager convManager = plugin.getConversationManager();
        boolean inChatMode = convManager.isInChatMode(playerId);
        convManager.setChatMode(playerId, !inChatMode);

        if (!inChatMode) {
            player.sendMessage(languageManager.getFeatureChatModeEnter());
            player.sendMessage(languageManager.getFeatureChatModeEnterSubtitle());
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogChatModeEntered(), "player", player.getName()));
        } else {
            player.sendMessage(languageManager.getFeatureChatModeExit());
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogChatModeExited(), "player", player.getName()));
        }
        return true;
    }

    /**
     * 处理 knowledge 命令
     */
    private boolean handleKnowledgeCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!PluginPermissionEnum.KNOWLEDGE.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionKnowledge());
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage(languageManager.getHelpKnowledge());
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handleKnowledgeReloadCommand(sender);
        } else {
            sender.sendMessage(languageManager.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(languageManager.getCommandAvailableSubcommands());
            return true;
        }
    }

    /**
     * 处理 knowledge reload 命令
     */
    private boolean handleKnowledgeReloadCommand(CommandSender sender) {
        try {
            plugin.getKnowledgeBase().reload();

            // 知识库内容变更后，重新分段 + 异步预计算（仅当 Embedding API 已配置时）
            EmbeddingService embeddingSvc = plugin.getEmbeddingService();
            if (embeddingSvc != null && embeddingSvc.isAvailable()) {
                embeddingSvc.clearCache();
                plugin.getKnowledgeRetriever().buildChunkCache();
                Map<String, List<String>> asyncChunks = plugin.getKnowledgeBase().getAllChunkCache();
                CompletableFuture.runAsync(() -> {
                    try {
                        embeddingSvc.precomputeAllChunks(asyncChunks);
                    } catch (Exception e) {
                        PluginLogger.warn("知识库", "Embedding 异步预计算异常: {}", e.getMessage());
                    }
                }, FoliaCompat.getIOPool());
            }

            sender.sendMessage(languageManager.getCommandKnowledgeReloadSuccess());
            sender.sendMessage("§7" + plugin.getKnowledgeBase().getStatistics());
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogKnowledgeReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandKnowledgeReloadFailure() + e.getMessage());
            PluginLogger.error("命令", languageManager.getLogAiRequestError() + e.getMessage(), e);
        }
        return true;
    }

    /**
     * 处理 personalities 命令（人格配置管理）
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handlePersonalitiesCommand(CommandSender sender, String[] args) {
        // 检查权限
        if (!PluginPermissionEnum.PERSONALITIES.hasPermission(sender)) {
            sender.sendMessage(languageManager.getPermissionPersonalities());
            return true;
        }

        // 如果没有子命令，显示帮助
        if (args.length < 2) {
            sender.sendMessage(languageManager.getHelpPersonalities());
            sender.sendMessage(languageManager.getHelpPersonalitiesSubcommands());
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            return handlePersonalitiesReloadCommand(sender);
        } else {
            sender.sendMessage(languageManager.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(languageManager.getCommandAvailableSubcommands());
            return true;
        }
    }

    /**
     * 处理 personalities reload 命令
     *
     * @param sender 命令发送者
     * @return 执行结果
     */
    private boolean handlePersonalitiesReloadCommand(CommandSender sender) {
        try {
            plugin.getPersonalitiesConfigManager().reload();
            sender.sendMessage(languageManager.getCommandPersonalitiesReloadSuccess());
            sender.sendMessage(I18nService.tr("§7当前共加载 {} 个人格", plugin.getPersonalitiesConfigManager().getAllPersonalities().size()));
            PluginLogger.info("命令", languageManager.replacePlaceholders(languageManager.getLogPersonalitiesReloaded(), "sender", getSenderName(sender)));
        } catch (Exception e) {
            sender.sendMessage(languageManager.getCommandPersonalitiesReloadFailure() + e.getMessage());
            PluginLogger.error("命令", languageManager.getLogAiRequestError() + e.getMessage(), e);
        }
        return true;
    }

    /**
     * 处理 plugins 命令（第三方插件调用）
     *
     * <p>命令格式：/kilacraft plugins <人格> <内容> <玩家 UUID> [回调命令...]</p>
     * <p>此命令只能由控制台执行，玩家禁止使用</p>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handlePluginsCommand(CommandSender sender, String[] args) {
        // 检查是否为控制台执行
        if (sender instanceof Player player) {
            player.sendMessage(languageManager.getPluginCommandPlayerBlocked());
            PluginLogger.warn("命令", languageManager.replacePlaceholders(languageManager.getLogPlayerCommandAttempt(), "player", player.getName()));
            return true;
        }

        // 检查参数数量（至少需要 3 个参数：人格、内容、UUID）
        if (args.length < 4) {
            sender.sendMessage(languageManager.getPluginCommandInsufficientArgs());
            sender.sendMessage(languageManager.getPluginCommandUsageExample());
            sender.sendMessage(languageManager.getPluginCommandCallbackHint());
            sender.sendMessage(languageManager.getPluginCommandCallbackExample());
            sender.sendMessage(languageManager.getPluginCommandCallbackPlaceholderHint());
            return true;
        }

        String personality = args[1];
        String message = args[2];
        String uuidString = args[3];
        // 可选的回调命令（第 5 个及之后的所有参数合并，支持含空格的命令）
        String callbackCommand = null;
        if (args.length >= 5) {
            // 将 args[4] 到 args[length-1] 合并为一个字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (i > 4) sb.append(" ");
                sb.append(args[i]);
            }
            callbackCommand = sb.toString();
        }
        final String finalCallbackCommand = callbackCommand;

        // 解析玩家 UUID
        UUID targetPlayerId;
        try {
            targetPlayerId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(languageManager.getPluginCommandInvalidUuid() + uuidString);
            sender.sendMessage(languageManager.getPluginCommandUuidFormatHint());
            return true;
        }

        // 获取目标玩家名称（用于显示和系统提示词）
        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerId);
        String targetPlayerName;
        if (targetPlayer != null) {
            targetPlayerName = targetPlayer.getName();
        } else {
            // 玩家不在线，尝试从离线数据获取
            String offlineName = plugin.getServer().getOfflinePlayer(targetPlayerId).getName();
            targetPlayerName = offlineName != null ? offlineName : "Unknown";
        }

        // 检查人格是否存在
        PersonalitiesConfigManager personalitiesConfig = plugin.getPersonalitiesConfigManager();
        if (!personalitiesConfig.hasPersonality(personality)) {
            sender.sendMessage(languageManager.getPluginCommandPersonalityNotFound() + personality);
            sender.sendMessage(languageManager.getPluginCommandPersonalityListHint() + String.join(", ", personalitiesConfig.getAllPersonalities()));
            return true;
        }

        // 如果目标玩家在线，检查世界限制
        if (targetPlayer != null) {
            if (!validator.canUseAIInWorld(targetPlayer)) {
                sender.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
                return true;
            }
        }

        // 检查插件命令专用冷却时间（基于 UUID，不管玩家是否在线）
        if (!validator.isPluginCommandCooldownReady(targetPlayerId)) {
            long remainingSeconds = validator.getPluginCommandRemainingCooldownSeconds(targetPlayerId);
            if (remainingSeconds > 0) {
                sender.sendMessage(languageManager.replacePlaceholders(languageManager.getPluginCommandCooldownWarning(), "player", targetPlayerName, "seconds", String.valueOf(remainingSeconds)));
            }
            return true;
        }

        // 生成隔离的历史记录 key（UUID_人格）
        String historyKey = validator.getPluginCommandHistoryKey(targetPlayerId, personality);

        // 获取或创建历史记录（使用隔离的 key）
        Deque<ConversationManager.Message> pluginHistory = getOrCreatePluginHistory(historyKey);

        // 立即更新冷却时间（基于 UUID）
        validator.startCooldown(targetPlayerId);

        // 创建插件命令响应处理器
        AIResponseHandler handler = new PluginCommandResponseHandler(sender, targetPlayerId, targetPlayerName);

        // 获取人格提示词并替换玩家名称占位符
        final String finalPersonality = personality;
        String personalityPrompt = personalitiesConfig.getPersonalityPrompt(personality).replace("{player}", targetPlayerName);

        PluginLogger.debug("命令", "插件命令请求 - 人格：{}, 玩家：{}, UUID: {}", personality, targetPlayerName, targetPlayerId);
        PluginLogger.debug("命令", "人格提示词：{}", personalityPrompt);

        // 异步加载历史（DB → 内存），然后发起 LLM 请求
        ConversationPersistenceService persistenceService = plugin.getPersistenceService();
        if (persistenceService != null) {
            persistenceService.loadHistoryIfNeeded(targetPlayerId, personality, loadedHistory -> {
                if (!loadedHistory.isEmpty() && pluginHistory.isEmpty()) {
                    for (ConversationManager.Message msg : loadedHistory) {
                        pluginHistory.addLast(msg);
                    }
                }
                PluginLogger.debug("命令", "历史记录数量：{}", pluginHistory.size());

                processPluginCommandLLMRequest(message, targetPlayerName, targetPlayerId, pluginHistory, handler, personalityPrompt, finalPersonality, finalCallbackCommand, sender);
            }, ConversationSource.PLUGIN);
        } else {
            PluginLogger.debug("命令", "历史记录数量：{}", pluginHistory.size());
            processPluginCommandLLMRequest(message, targetPlayerName, targetPlayerId, pluginHistory, handler, personalityPrompt, finalPersonality, finalCallbackCommand, sender);
        }
        return true;
    }

    private void processPluginCommandLLMRequest(String message, String targetPlayerName, UUID targetPlayerId, Deque<ConversationManager.Message> pluginHistory, AIResponseHandler handler, String personalityPrompt, String finalPersonality, String finalCallbackCommand, CommandSender sender) {
        // 使用统一的 API 处理请求（传入人格提示词）
        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(message, targetPlayerName, pluginHistory, handler, personalityPrompt).thenAccept(fullResponse -> {
            // 保存对话到历史记录（隔离的），并保存到最新回复缓存
            validator.saveToHistory(pluginHistory, message, fullResponse, targetPlayerId, finalPersonality);

            // 执行回调命令（如果指定了）- 配置驱动型插件集成
            if (finalCallbackCommand != null && !finalCallbackCommand.isEmpty()) {
                int timeoutSeconds = plugin.getConfigManager().getCallbackTimeoutSeconds();
                long startTime = System.currentTimeMillis();

                // 使用 FutureTask 包装命令执行，实现超时控制
                FutureTask<Void> task = new FutureTask<>(() -> {
                    executeCallback(finalCallbackCommand, fullResponse);
                    return null;
                });

                // 提交到全局区域执行（Folia）/ 主线程执行（Spigot）
                FoliaCompat.runTask(plugin, task);

                // 等待完成或超时
                try {
                    task.get(timeoutSeconds, TimeUnit.SECONDS);

                    long elapsed = System.currentTimeMillis() - startTime;
                    PluginLogger.debug("命令", String.format("回调执行耗时: %dms", elapsed));

                    // 如果执行时间超过 80% 的阈值，记录警告
                    if (elapsed > timeoutSeconds * 800L) {
                        PluginLogger.warn("命令", String.format("回调命令执行较慢 (%dms)，接近超时阈值 (%ds)", elapsed, timeoutSeconds));
                    }

                } catch (TimeoutException e) {
                    task.cancel(true);
                    PluginLogger.warn("命令", String.format("回调命令执行超时 (%ds)，已强制中断。命令: %s", timeoutSeconds, finalCallbackCommand.length() > 100 ? finalCallbackCommand.substring(0, 100) + "..." : finalCallbackCommand));
                } catch (CancellationException e) {
                    // 任务被取消（超时），已在上面记录日志，这里不重复打印
                } catch (ExecutionException e) {
                    PluginLogger.warn("命令", I18nService.tr("回调命令执行失败: {}", e.getCause().getMessage()));
                    PluginLogger.debug("命令", "原始命令: {}", finalCallbackCommand);
                } catch (InterruptedException e) {
                    PluginLogger.warn("命令", I18nService.tr("回调命令执行被中断: {}", e.getMessage()), e);
                    Thread.currentThread().interrupt(); // 恢复中断状态
                } catch (Exception e) {
                    PluginLogger.warn("命令", I18nService.tr("回调命令执行异常: {}", e.getMessage()), e);
                } finally {
                    // 立即删除缓存
                    plugin.getConversationManager().pollLatestAIResponse(targetPlayerId, finalPersonality);

                    PluginLogger.debug("命令", "回调命令执行完毕，缓存已删除");
                }
            } else {
                // 保留缓存供轮询获取
                PluginLogger.debug("命令", "保留缓存供轮询获取");
            }

            PluginLogger.debug("命令", "插件命令响应完成，响应长度：{}", fullResponse.length());
        }).exceptionally(throwable -> {
            sender.sendMessage(languageManager.getPluginCommandError() + throwable.getMessage());
            PluginLogger.error("命令", languageManager.getLogPluginCommandAiError() + throwable.getMessage(), throwable);
            return null;
        });
    }

    /**
     * 获取或创建插件命令的历史记录
     *
     * @param historyKey 历史记录 key（格式：UUID_人格）
     * @return 历史记录队列
     */
    private Deque<ConversationManager.Message> getOrCreatePluginHistory(String historyKey) {
        return plugin.getConversationManager().getOrCreatePluginHistory(historyKey);
    }

    /**
     * 执行回调命令
     *
     * @param callbackCommand 回调命令模板（支持 {response} 占位符）
     * @param response        AI 回复内容
     */
    private void executeCallback(String callbackCommand, String response) {
        // 替换占位符
        String finalCommand = callbackCommand.replace("{response}", response.replace("\"", "\\\""));

        PluginLogger.debug("命令", "执行回调命令: {}", finalCommand);

        // 以控制台身份执行回调命令
        FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
    }

    /**
     * 处理普通消息命令
     */
    private boolean handleNormalMessageCommand(CommandSender sender, String[] args) {
        // 检查是否为玩家（非玩家跳过冷却和 API 调用）
        if (sender instanceof Player player) {
            return handlePlayerMessageCommand(player, args);
        } else {
            return handleConsoleMessageCommand(sender, args);
        }
    }

    /**
     * 处理玩家消息命令
     */
    private boolean handlePlayerMessageCommand(Player player, String[] args) {
        // 检查世界限制
        if (!validator.canUseAIInWorld(player)) {
            player.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
            return true;
        }

        // 检查冷却时间
        UUID playerId = player.getUniqueId();
        if (!validator.isCooldownReady(playerId)) {
            long remainingSeconds = validator.getRemainingCooldownSeconds(playerId);
            if (remainingSeconds > 0) {
                player.sendMessage(languageManager.replacePlaceholders(languageManager.getCooldownWarning(), "seconds", String.valueOf(remainingSeconds)));
            }
            return true;
        }

        // 构建消息
        String message = String.join(" ", args);

        // 发送"正在思考"消息
        MessageUtil.sendThinkingMessage(player);
        // 立即更新冷却时间
        validator.startCooldown(playerId);

        // 获取历史记录（支持从数据库加载）
        ConversationPersistenceService persistenceService = plugin.getPersistenceService();
        if (persistenceService != null) {
            ConversationManager convManager = plugin.getConversationManager();
            Deque<ConversationManager.Message> playerHistory = convManager.getOrCreateHistory(playerId);

            persistenceService.loadHistoryIfNeeded(playerId, "", loadedHistory -> {
                // 如果是从DB加载的，回填到内存缓存
                if (!loadedHistory.isEmpty() && playerHistory.isEmpty()) {
                    for (ConversationManager.Message msg : loadedHistory) {
                        playerHistory.addLast(msg);
                    }
                }
                PluginLogger.debug("命令", "玩家 {} 的历史记录数量：{}", player.getName(), playerHistory.size());

                // 使用统一的 AI 请求处理器
                boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
                aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSource.COMMAND);
            }, ConversationSource.COMMAND, ConversationSource.CHAT);
        } else {
            // 无持久化服务，使用原有同步逻辑
            Deque<ConversationManager.Message> playerHistory = getOrCreateHistory(playerId);
            PluginLogger.debug("命令", "玩家 {} 的历史记录数量：{}", player.getName(), playerHistory.size());

            boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
            aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSource.COMMAND);
        }
        return true;
    }

    /**
     * 获取或创建历史记录
     */
    private Deque<ConversationManager.Message> getOrCreateHistory(UUID playerId) {
        ConversationManager convManager = plugin.getConversationManager();
        Deque<ConversationManager.Message> history = convManager.getHistory(playerId);

        if (history == null) {
            history = convManager.getHistory().computeIfAbsent(playerId, k -> new ArrayDeque<>());
        }

        return history;
    }

    /**
     * 处理控制台消息命令
     */
    private boolean handleConsoleMessageCommand(CommandSender sender, String[] args) {
        // 构建消息
        String message = String.join(" ", args);

        // 发送"正在思考"消息
        MessageUtil.sendThinkingMessage(sender);

        // 使用统一的 AI 请求处理器（传入 null 表示控制台）
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
        aiRequestHandler.handleAIRequestForConsole(sender, message, enableAgent);
        return true;
    }

    /**
     * 获取发送者名称
     */
    private String getSenderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "Console";
    }

    /**
     * 处理 tasks 命令（查看定时任务运行状态）
     */
    private boolean handleTasksCommand(CommandSender sender) {
        if (!PluginPermissionEnum.TASKS.hasPermission(sender)) {
            sender.sendMessage(I18nService.tr("§c你没有权限查看定时任务状态。"));
            return true;
        }

        var scheduler = plugin.getTaskScheduler();
        if (scheduler == null) {
            sender.sendMessage(I18nService.tr("§cTaskScheduler 未初始化。"));
            return true;
        }

        for (String line : scheduler.getStatusSummary()) {
            sender.sendMessage(line);
        }
        return true;
    }

    /**
     * 处理 afk 子命令
     *
     * <p>命令格式：</p>
     * <ul>
     *   <li>/kilacraft afk 或 /kilacraft afk query - 查询当前挂机任务</li>
     *   <li>/kilacraft afk cancel - 取消当前挂机任务</li>
     * </ul>
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 执行结果
     */
    private boolean handleAfkCommand(CommandSender sender, String[] args) {
        // 仅限玩家使用
        if (!(sender instanceof Player player)) {
            sender.sendMessage(I18nService.tr("§c挂机任务命令仅限玩家使用。"));
            return true;
        }

        AFKTaskManager manager = plugin.getAfkTaskManager();
        if (manager == null) {
            player.sendMessage(I18nService.tr("§c挂机任务系统未启用。"));
            return true;
        }

        // 解析子命令
        String subAction = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "query";

        switch (subAction) {
            case "cancel" -> {
                if (!manager.hasTask(player.getUniqueId())) {
                    player.sendMessage(I18nService.tr("§7你当前没有正在运行的挂机任务。"));
                    return true;
                }
                AFKTask task = manager.getTask(player.getUniqueId());
                manager.cancelTask(player.getUniqueId());
                player.sendMessage(I18nService.tr("§a已取消挂机任务：§f{}", task.getTaskDescription()));
            }
            case "query", "" -> {
                if (!manager.hasTask(player.getUniqueId())) {
                    player.sendMessage(I18nService.tr("§7你当前没有正在运行的挂机任务。"));
                    return true;
                }
                AFKTask task = manager.getTask(player.getUniqueId());
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                player.sendMessage(I18nService.tr("§f当前挂机任务："));
                player.sendMessage(I18nService.tr("§f  任务ID：§e{}", task.getTaskId()));
                player.sendMessage(I18nService.tr("§f  类型：§e{}", task.getTaskType().getLocalizedDescription()));
                player.sendMessage(I18nService.tr("§f  描述：§e{}", task.getTaskDescription()));
                player.sendMessage(I18nService.tr("§f  状态：§e{}", task.getStatusText()));
                player.sendMessage(I18nService.tr("§f  创建时间：§e{}", sdf.format(new Date(task.getCreatedAt()))));
                player.sendMessage(I18nService.tr("§7使用 /kilacraft afk cancel 可取消此任务"));
            }
            default -> {
                player.sendMessage(I18nService.tr("§c未知的挂机任务子命令：{}", subAction));
                player.sendMessage(I18nService.tr("§7用法：/kilacraft afk [query|cancel]"));
            }
        }
        return true;
    }

}
