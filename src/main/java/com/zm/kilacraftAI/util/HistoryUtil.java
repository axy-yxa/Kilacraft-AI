package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.manager.ConversationManager;

import java.util.Deque;

/**
 * 历史记录工具类
 * <p>
 * 提供统一的历史记录格式化和处理功能
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class HistoryUtil {

    /**
     * 构建统一的历史记录显示格式
     *
     * @param history       对话历史
     * @param configManager 配置管理器，用于轮数转换
     * @param roundsConfig  配置的轮数
     * @return 格式化的历史记录字符串
     */
    public static String buildHistoryDisplay(Deque<ConversationManager.Message> history, ConfigManager configManager, int roundsConfig) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        int maxMessages = configManager.getHistoryMessageCount(roundsConfig);
        if (maxMessages <= 0) {
            return "";
        }

        StringBuilder historyBuilder = new StringBuilder();
        historyBuilder.append(I18nService.tr("[对话历史]\n"));

        int totalSize = history.size();
        int skipCount = Math.max(0, totalSize - maxMessages);
        int count = 0;

        // 从头部开始遍历，跳过最老的记录，保留最新的记录
        for (ConversationManager.Message msg : history) {
            // 跳过最老的记录
            if (skipCount > 0) {
                skipCount--;
                continue;
            }

            // 添加最新的记录
            if (count++ >= maxMessages) break;

            // 统一的角色显示格式
            String roleDisplay = switch (msg.getRole()) {
                case "user" -> I18nService.tr("用户");
                case "assistant" -> "AI";
                default -> msg.getRole();
            };

            historyBuilder.append("- ").append(roleDisplay).append(": ").append(msg.getContent()).append("\n");
        }
        historyBuilder.append("\n");

        return historyBuilder.toString();
    }
}