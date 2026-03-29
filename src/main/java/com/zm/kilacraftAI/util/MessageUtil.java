package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 消息格式化工具类
 * 用于统一管理 AI 相关的消息格式
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class MessageUtil {

    /**
     * 获取 AI 消息前缀
     *
     * @return 配置的前缀字符串（包含颜色代码）
     */
    public static String getAIPrefix() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return "§7[Kilacraft-AI] §f"; // 默认前缀
        }
        return plugin.getConfigManager().getAiPrefix();
    }

    /**
     * 获取 AI 名称（从配置文件直接读取）
     *
     * @return AI 名称
     */
    public static String getAIName() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return "Kilacraft-AI"; // 默认值
        }
        return plugin.getConfigManager().getAiName();
    }

    /**
     * 获取"正在思考"提示文本
     *
     * @return 配置的思考中文本
     */
    public static String getThinkingMessage() {
        return KilacraftAI.getInstance().getConfigManager().getThinkingMessage();
    }

    /**
     * 获取完整的"正在思考"消息（带前缀）
     *
     * @return 完整的消息字符串
     */
    public static String getFullThinkingMessage() {
        return getAIPrefix() + getThinkingMessage();
    }

    /**
     * 向玩家发送"正在思考"消息
     *
     * @param player 玩家对象
     */
    public static void sendThinkingMessage(Player player) {
        if (player != null) {
            player.sendMessage(getFullThinkingMessage());
        }
    }

    /**
     * 向命令发送者发送"正在思考"消息
     *
     * @param sender 命令发送者
     */
    public static void sendThinkingMessage(CommandSender sender) {
        if (sender != null) {
            sender.sendMessage(getFullThinkingMessage());
        }
    }
}
