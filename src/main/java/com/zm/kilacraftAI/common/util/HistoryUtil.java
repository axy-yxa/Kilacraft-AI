package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.conversation.ConversationManager;

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

            // 统一的角色显示格式；assistant 按 source 追加来源标签（如 AI[问候]）
            String roleDisplay = switch (msg.getRole()) {
                case "user" -> I18nService.tr("用户");
                case "assistant" -> appendSourceTag(msg.getSource());
                default -> msg.getRole();
            };

            historyBuilder.append("- ").append(roleDisplay).append(": ").append(msg.getContent()).append("\n");
        }
        historyBuilder.append("\n");

        return historyBuilder.toString();
    }

    /**
     * 根据 {@link ConversationManager.Message#getSource()} 渲染 assistant 角色显示文本。
     *
     * <p>问候等非普通对话来源在 {@code AI} 后追加来源标签（如 {@code AI[问候]}），
     * 与 Phase2 意图识别 system prompt 中承诺的「AI[问候]: 内容」格式一致。
     * 普通对话来源（chat/command/plugin 及 null 旧记录）不追加标签。</p>
     */
    private static String appendSourceTag(String source) {
        if (source != null && !source.isEmpty() && !ConversationSourceEnum.CHAT.getValue().equals(source) && !ConversationSourceEnum.COMMAND.getValue().equals(source) && !ConversationSourceEnum.PLUGIN.getValue().equals(source)) {
            return "AI" + sourceTag(source);
        }
        return "AI";
    }

    /**
     * 将 source 标识映射为可读标签文本（走 i18n，按语言本地化）。
     * 当前仅 greeting 有对应标签；未知 source 原样作为 [source] 显示，便于未来扩展。
     */
    private static String sourceTag(String source) {
        if (ConversationSourceEnum.GREETING.getValue().equals(source)) {
            return I18nService.tr("[问候]");
        }
        return "[" + source + "]";
    }
}