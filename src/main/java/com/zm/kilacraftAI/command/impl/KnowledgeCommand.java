package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.service.knowledge.EmbeddingService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * /kila knowledge reload：知识库管理（knowledge 权限）。
 *
 * @author Zm_Mmm
 * @since 2026-06-25
 */
public final class KnowledgeCommand {

    private KnowledgeCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.KNOWLEDGE.hasPermission(sender)) {
            sender.sendMessage(lm.getPermissionKnowledge());
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(lm.getHelpKnowledge());
            return;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            handleReload(plugin, sender, lm);
            return;
        } else {
            sender.sendMessage(lm.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(lm.getCommandAvailableSubcommands());
            return;
        }
    }

    private static void handleReload(KilacraftAI plugin, CommandSender sender, LanguageManager lm) {
        String senderName = sender instanceof Player p ? p.getName() : "Console";
        try {
            plugin.getKnowledgeBase().reload();
            plugin.getKnowledgeRetriever().buildChunkCache();
            plugin.getKnowledgeRetriever().computeAvgDocLength();

            if (plugin.getConfigManager().isCustomDictionaryEnabled()) {
                TextProcessorFactory.reset();
                TextProcessorFactory.initialize(plugin.getKnowledgeBase().buildDictionaryWordsWithCorpus(plugin.getConfigManager().getAllDictionaryWords()));
            }

            EmbeddingService embeddingSvc = plugin.getEmbeddingService();
            if (embeddingSvc != null && embeddingSvc.isAvailable()) {
                embeddingSvc.clearCache();
                Map<String, List<String>> asyncChunks = plugin.getKnowledgeBase().getAllChunkCache();
                CompletableFuture.runAsync(() -> {
                    try {
                        embeddingSvc.precomputeAllChunks(asyncChunks);
                    } catch (Exception e) {
                        PluginLoggerUtil.warn("知识库", "Embedding 异步预计算异常: {}", e.getMessage());
                    }
                }, FoliaCompat.getIOPool());
            }

            sender.sendMessage(lm.getCommandKnowledgeReloadSuccess());
            sender.sendMessage("§7" + plugin.getKnowledgeBase().getStatistics());
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogKnowledgeReloaded(), "sender", senderName));
        } catch (Exception e) {
            sender.sendMessage(lm.getCommandKnowledgeReloadFailure() + e.getMessage());
            PluginLoggerUtil.error("命令", lm.getLogAiRequestError() + e.getMessage(), e);
        }
    }
}
