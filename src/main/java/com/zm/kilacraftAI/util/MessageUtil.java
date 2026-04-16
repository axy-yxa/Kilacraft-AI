package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;

import java.util.regex.Pattern;

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
     * 向玩家发送“正在思考”消息
     *
     * <p>与 AI 输出管线联动，支持配置化的输出载体（CHAT/ACTION_BAR/BOSS_BAR/TITLE）。</p>
     * <p>如果启用流式输出，则显示流式占位符（由 StreamOutputManager 管理）。</p>
     *
     * @param player 玩家对象
     */
    public static void sendThinkingMessage(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        KilacraftAI plugin = KilacraftAI.getInstance();
        if (plugin == null) {
            // 降级处理：直接发送到 CHAT
            player.sendMessage(getFullThinkingMessage());
            return;
        }

        OutputConfigManager outputConfigManager = plugin.getConfigManager().getOutputConfigManager();

        // 如果启用流式输出，立即显示流式占位符（窗口期管理）
        if (outputConfigManager.isStreamEnabled()) {
            OutputChannel channel = outputConfigManager.getThinkingChannel();
            plugin.getStreamOutputManager().startGeneration(player, channel);
            return;
        }

        // 使用配置的“正在思考”输出载体发送消息
        OutputChannel channel = outputConfigManager.getThinkingChannel();
        String message = getFullThinkingMessage();

        // 通过 AIResponsePipeline 发送思考消息（使用动态配置的载体）
        plugin.getResponsePipeline().sendThinking(player, message, channel);
    }

    /**
     * 向命令发送者发送"正在思考"消息
     *
     * <p>控制台发送者始终使用 CHAT 载体（sendMessage）。</p>
     *
     * @param sender 命令发送者
     */
    public static void sendThinkingMessage(CommandSender sender) {
        if (sender != null) {
            sender.sendMessage(getFullThinkingMessage());
        }
    }

    // Markdown 加粗匹配模式：**text**
    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    // Markdown 斜体匹配模式：*text*
    private static final Pattern MARKDOWN_ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    // Markdown 行内代码匹配模式：`text`
    private static final Pattern MARKDOWN_CODE = Pattern.compile("`(.+?)`");

    /**
     * 将 Markdown 格式转换为 Minecraft 颜色代码
     *
     * <p>转换规则：
     * <ul>
     *   <li>**text** → §ltext§r（加粗）
     *   <li>*text* → §otext§r（斜体，避免与加粗冲突）
     *   <li>`text` → §7text§r（灰色，模拟代码样式）
     * </ul>
     *
     * @param message 包含 Markdown 格式的原始消息
     * @return 转换后的 Minecraft 颜色代码消息
     */
    public static String convertMarkdownToMinecraft(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // 先处理行内代码（避免内部 ** 被错误处理）
        message = MARKDOWN_CODE.matcher(message).replaceAll("§7$1§r");
        // 处理加粗 **text**
        message = MARKDOWN_BOLD.matcher(message).replaceAll("§l$1§r");
        // 处理斜体 *text*
        message = MARKDOWN_ITALIC.matcher(message).replaceAll("§o$1§r");

        return message;
    }
}
