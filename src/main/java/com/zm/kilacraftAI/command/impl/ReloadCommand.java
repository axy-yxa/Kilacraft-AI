package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.model.DatabaseConfig;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        try {
            plugin.getConfigManager().loadConfig();
            plugin.getI18nService().reload();
            plugin.getLanguageManager().loadConfig();

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
}
