package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.model.DatabaseConfig;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.service.knowledge.EmbeddingService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * /kila reload：热重载全部配置（reload 权限）。
 */
public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.RELOAD.hasPermission(sender)) {
            sender.sendMessage(lm.getPermissionReload());
            return;
        }

        String senderName = sender instanceof Player p ? p.getName() : "Console";
        // 快照重载前的语言/Embedding 状态，用于检测是否需要重建知识库（避免缓存与新语言 chunk 失配）
        boolean prevIsChinese = plugin.getConfigManager().isChinese();
        boolean prevEmbeddingEnabled = plugin.getConfigManager().isEmbeddingEnabled();
        try {
            plugin.getConfigManager().loadConfig();
            plugin.getI18nService().reload();
            plugin.getLanguageManager().loadConfig();

            // 刷新 LLM 预算/熔断阈值（D6/D13），使 llm.yml 改动即时生效
            if (plugin.getLlmOutputCoordinator() != null) {
                plugin.getLlmOutputCoordinator().refreshBudget();
            }

            // 重载守护系统配置
            if (plugin.getGuardianConfigManager() != null) {
                plugin.getGuardianConfigManager().reload();
                if (plugin.getGuardianManager() != null) {
                    plugin.getGuardianManager().reloadAll();
                }
                PluginLoggerUtil.info("热重载", I18nService.tr("守护配置已重载，在线玩家守护已重建"));
            }

            // 重载自定义监听配置，重建轮询定时器
            if (plugin.getWatchConfigManager() != null) {
                plugin.getWatchConfigManager().reload();
                if (plugin.getWatchService() != null) {
                    plugin.getWatchService().onConfigReload();
                }
                PluginLoggerUtil.info("热重载", I18nService.tr("自定义监听配置已重载"));
            }

            // 重载 Web 搜索与抓取配置
            if (plugin.getWebConfigManager() != null) {
                plugin.getWebConfigManager().reload();
                PluginLoggerUtil.info("热重载", I18nService.tr("Web 搜索配置已重载"));
            }

            // 重载对话推荐配置
            if (plugin.getSuggestionConfigManager() != null) {
                plugin.getSuggestionConfigManager().reload();
            }

            // 重载问候配置（behavior.yml greeting 段）
            if (plugin.getConfigManager().getGreetingConfigManager() != null) {
                plugin.getConfigManager().getGreetingConfigManager().reload();
            }

            // 重载工具通知提示词（behavior.yml utility 段）
            if (plugin.getUtilityConfigManager() != null) {
                plugin.getUtilityConfigManager().reload();
            }

            if (plugin.getSkillConfigManager() != null) {
                plugin.getSkillConfigManager().reloadAllConfigs();
            }
            plugin.syncConditionalSkills();

            if (plugin.getAdminConfigManager() != null) {
                plugin.getAdminConfigManager().reload();
                plugin.syncGuardianState();
            }
            if (plugin.getIntentPromptConfigManager() != null) {
                plugin.getIntentPromptConfigManager().reload();
            }
            if (plugin.getSoundEffectManager() != null) {
                plugin.getSoundEffectManager().loadConfig();
            }
            if (plugin.getPersonalitiesConfigManager() != null) {
                plugin.getPersonalitiesConfigManager().reload();
            }

            if (plugin.getDatabaseConfigManager() != null && plugin.getDatabaseManager() != null) {
                try {
                    plugin.getDatabaseConfigManager().reload();
                    DatabaseConfig newDbConfig = plugin.getDatabaseConfigManager().getConfig();
                    plugin.getDatabaseManager().reload(newDbConfig);

                    String newServerId = newDbConfig.getServerId();
                    if (plugin.getPersistenceService() != null)
                        plugin.getPersistenceService().refreshConfig(newDbConfig);
                    if (plugin.getDataCleanupService() != null)
                        plugin.getDataCleanupService().refreshConfig(newDbConfig);
                    if (plugin.getProfileAnalysisService() != null) plugin.getProfileAnalysisService().refreshConfig();
                    if (plugin.getProfileManager() != null) plugin.getProfileManager().refreshConfig();
                    if (plugin.getEventCollector() != null) plugin.getEventCollector().refreshConfig(newServerId);
                    if (plugin.getMarketEventCollector() != null)
                        plugin.getMarketEventCollector().refreshConfig(newServerId);
                    if (plugin.getSocialGraph() != null) plugin.getSocialGraph().refreshConfig();
                    if (plugin.getSocialRelationExtractor() != null)
                        plugin.getSocialRelationExtractor().refreshConfig(newServerId);
                    if (plugin.getOfflineEventAggregator() != null) plugin.getOfflineEventAggregator().refreshConfig();
                    if (plugin.getProfileManager() != null) plugin.getProfileManager().reconcileOnlineProfiles();
                } catch (Exception dbEx) {
                    PluginLoggerUtil.error("热重载", "数据库热重载失败，已回退到旧配置: {}", dbEx.getMessage());
                }
            }

            if (plugin.getKnowledgeRetriever() != null) {
                ConfigManager cm = plugin.getConfigManager();
                plugin.getKnowledgeRetriever().refreshConfig(cm.getMaxRelevantChunks(), cm.getKnowledgeMaxChunkSize(), cm.getKnowledgeMinChunkSize(), cm.getKnowledgeChunkOverlap(), cm.getKeywordTopK(), cm.getBm25K1(), cm.getBm25B());
                plugin.getKnowledgeRetriever().setRetrievalConfig(cm.getRetrievalNoiseFloor(), cm.getRetrievalRelativeThreshold(), cm.getRetrievalRrfK(), cm.getBm25AvgDocLength());
            }

            if (plugin.getEmbeddingService() != null) {
                boolean enabled = plugin.getConfigManager().isEmbeddingEnabled();
                plugin.getKnowledgeRetriever().setEmbeddingService(plugin.getEmbeddingService(), enabled, plugin.getConfigManager().getEmbeddingMinSimilarity());
                if (!enabled) {
                    PluginLoggerUtil.info("热重载", "Embedding 已关闭，降级到 BM25 检索");
                }
            }

            // 检测语言或 Embedding 开关变更：触发知识库全刷新，避免 Embedding 缓存与新语言 chunk 失配
            boolean langOrEmbeddingChanged = (plugin.getConfigManager().isChinese() != prevIsChinese) || (plugin.getConfigManager().isEmbeddingEnabled() != prevEmbeddingEnabled);
            if (langOrEmbeddingChanged) {
                refreshKnowledgeAfterConfigChange(plugin);
                PluginLoggerUtil.info("热重载", "检测到语言或 Embedding 配置变更，已重建知识库分块、BM25 统计与 Embedding 向量缓存");
            }

            TextProcessorFactory.reset();
            if (plugin.getConfigManager().isCustomDictionaryEnabled()) {
                TextProcessorFactory.initialize(plugin.getKnowledgeBase().buildDictionaryWordsWithCorpus(plugin.getConfigManager().getAllDictionaryWords()));
            }

            sender.sendMessage(lm.getCommandReloadSuccess());
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogConfigReloaded(), "sender", senderName));
        } catch (Exception e) {
            sender.sendMessage(lm.getCommandReloadFailure() + e.getMessage());
            PluginLoggerUtil.error("命令", lm.getLogAiRequestError() + e.getMessage(), e);
        }
    }

    /**
     * 语言或 Embedding 配置变更后，重建知识库：分块、BM25 统计、Embedding 向量缓存。
     * 复用 /kila knowledge reload 的核心流程，避免 /kila reload 切语言后缓存与 chunk 失配。
     */
    private static void refreshKnowledgeAfterConfigChange(KilacraftAI plugin) {
        plugin.getKnowledgeBase().reload();
        plugin.getKnowledgeRetriever().buildChunkCache();
        plugin.getKnowledgeRetriever().computeAvgDocLength();
        EmbeddingService embeddingSvc = plugin.getEmbeddingService();
        if (embeddingSvc != null && embeddingSvc.isAvailable()) {
            embeddingSvc.clearCache();
            Map<String, List<String>> asyncChunks = plugin.getKnowledgeBase().getAllChunkCache();
            CompletableFuture.runAsync(() -> {
                try {
                    embeddingSvc.precomputeAllChunks(asyncChunks);
                } catch (Exception e) {
                    PluginLoggerUtil.warn("热重载", "Embedding 异步预计算异常: {}", e.getMessage());
                }
            }, FoliaCompat.getIOPool());
        }
    }
}
