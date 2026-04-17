package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * 语言配置管理器
 * 负责加载和管理 language.yml 中的所有系统提示文本
 *
 * @author Zm_Mmm
 * @since 2026-03-29
 */
public class LanguageManager {

    private final JavaPlugin plugin;
    @Getter
    private FileConfiguration config;

    // ==================== 帮助消息 ====================
    @Getter
    private List<String> helpMessages;
    @Getter
    private String helpClearSelf;
    @Getter
    private String helpClearOther;
    @Getter
    private String helpKnowledge;
    @Getter
    private String helpPersonalities;
    @Getter
    private String helpPersonalitiesSubcommands;
    @Getter
    private String helpAfk;
    @Getter
    private String helpAfkSubcommands;

    // ==================== 权限相关消息 ====================
    @Getter
    private String permissionReload;
    @Getter
    private String permissionClearSelf;
    @Getter
    private String permissionClearOther;
    @Getter
    private String permissionKnowledge;
    @Getter
    private String permissionPersonalities;

    // ==================== 功能状态消息 ====================
    @Getter
    private String featureChatModeDisabled;
    @Getter
    private String featureChatModePlayerOnly;
    @Getter
    private String featureChatModeEnter;
    @Getter
    private String featureChatModeEnterSubtitle;
    @Getter
    private String featureChatModeExit;

    // ==================== 命令执行结果消息 ====================
    @Getter
    private String commandReloadSuccess;
    @Getter
    private String commandReloadFailure;
    @Getter
    private String commandClearSelfSuccess;
    @Getter
    private String commandClearOtherSuccess;
    @Getter
    private String commandClearConsoleHint;
    @Getter
    private String commandKnowledgeReloadSuccess;
    @Getter
    private String commandKnowledgeReloadFailure;
    @Getter
    private String commandPersonalitiesReloadSuccess;
    @Getter
    private String commandPersonalitiesReloadFailure;
    @Getter
    private String commandUnknownSubcommand;
    @Getter
    private String commandAvailableSubcommands;
    @Getter
    private String cooldownWarning;
    @Getter
    private String pluginCommandCooldownWarning;
    @Getter
    private String worldBannedHint;

    // ==================== 插件命令专用消息 ====================
    @Getter
    private String pluginCommandPlayerBlocked;
    @Getter
    private String pluginCommandInsufficientArgs;
    @Getter
    private String pluginCommandUsageExample;
    @Getter
    private String pluginCommandCallbackHint;
    @Getter
    private String pluginCommandCallbackExample;
    @Getter
    private String pluginCommandCallbackPlaceholderHint;
    @Getter
    private String pluginCommandInvalidUuid;
    @Getter
    private String pluginCommandUuidFormatHint;
    @Getter
    private String pluginCommandPersonalityNotFound;
    @Getter
    private String pluginCommandPersonalityListHint;
    @Getter
    private String pluginCommandError;

    // ==================== 日志消息 ====================
    @Getter
    private String logConfigReloaded;
    @Getter
    private String logKnowledgeReloaded;
    @Getter
    private String logPersonalitiesReloaded;
    @Getter
    private String logChatModeEntered;
    @Getter
    private String logChatModeExited;
    @Getter
    private String logClearSelfLogged;
    @Getter
    private String logClearOtherLogged;
    @Getter
    private String logPlayerCommandAttempt;
    @Getter
    private String logAiRequestError;
    @Getter
    private String logConsoleAiError;
    @Getter
    private String logPluginCommandAiError;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载语言配置文件
     */
    public void loadConfig() {
        // 复制默认配置
        ConfigResourceUtil.saveDefaultResource((KilacraftAI) plugin, "language.yml", "语言配置");

        // 手动加载 language.yml 文件内容到内存
        try {
            File languageFile = new File(plugin.getDataFolder(), "language.yml");
            // 使用 YamlConfiguration 直接读取 language.yml
            this.config = YamlConfiguration.loadConfiguration(languageFile);
        } catch (Exception e) {
            PluginLogger.error("语言配置", "加载 language.yml 失败", e);
        }

        // 加载语言配置
        loadLanguageConfig();
    }

    /**
     * 从配置文件中读取所有语言项
     */
    private void loadLanguageConfig() {
        // 帮助消息
        this.helpMessages = config.getStringList("help.messages");
        this.helpClearSelf = config.getString("help.clear-self", "§e清除历史：/kilacraft clear");
        this.helpClearOther = config.getString("help.clear-other", "§e清除玩家历史：/kilacraft clear [玩家名称]");
        this.helpKnowledge = config.getString("help.knowledge", "§e使用方法：/kilacraft knowledge reload");
        this.helpPersonalities = config.getString("help.personalities", "§e使用方法：/kilacraft personalities reload");
        this.helpPersonalitiesSubcommands = config.getString("help.personalities-subcommands", "§e可用子命令：reload - 重新加载人格配置");
        this.helpAfk = config.getString("help.afk", "§e查询挂机任务：/kilacraft afk");
        this.helpAfkSubcommands = config.getString("help.afk-subcommands", "§e可用子命令：query(查询), cancel(取消)");

        // 权限相关消息
        this.permissionReload = config.getString("permissions.reload", "§c你没有权限重载配置！");
        this.permissionClearSelf = config.getString("permissions.clear-self", "§c你没有权限清除自己的对话历史记录！");
        this.permissionClearOther = config.getString("permissions.clear-other", "§c你没有权限清除其他玩家的历史记录！");
        this.permissionKnowledge = config.getString("permissions.knowledge", "§c你没有权限管理知识库！");
        this.permissionPersonalities = config.getString("permissions.personalities", "§c你没有权限管理人格配置！");

        // 功能状态消息
        this.featureChatModeDisabled = config.getString("features.chat-mode-disabled", "§c连续对话模式已被禁用！");
        this.featureChatModePlayerOnly = config.getString("features.chat-mode-player-only", "§c只有玩家才能使用连续对话模式！");
        this.featureChatModeEnter = config.getString("features.chat-mode-enter", "§a已进入连续对话模式！现在你说的每句话都会发送给 Kilacraft-AI。");
        this.featureChatModeEnterSubtitle = config.getString("features.chat-mode-enter-subtitle", "§7输入 §e/kilacraft chat§7 退出连续对话模式");
        this.featureChatModeExit = config.getString("features.chat-mode-exit", "§7已退出连续对话模式");

        // 命令执行结果消息
        this.commandReloadSuccess = config.getString("commands.reload-success", "§a配置已重载！");
        this.commandReloadFailure = config.getString("commands.reload-failure", "§c配置重载失败：");
        this.commandClearSelfSuccess = config.getString("commands.clear-self-success", "§a已清除你的对话历史记录！");
        this.commandClearOtherSuccess = config.getString("commands.clear-other-success", "§a已清除玩家 {player} 的对话历史记录！");
        this.commandClearConsoleHint = config.getString("commands.clear-console-hint", "§c请在命令中添加玩家名称来清除其历史记录：/kilacraft clear <玩家名称>");
        this.commandKnowledgeReloadSuccess = config.getString("commands.knowledge-reload-success", "§a知识库已重载！");
        this.commandKnowledgeReloadFailure = config.getString("commands.knowledge-reload-failure", "§c知识库重载失败：");
        this.commandPersonalitiesReloadSuccess = config.getString("commands.personalities-reload-success", "§a人格配置已重载！");
        this.commandPersonalitiesReloadFailure = config.getString("commands.personalities-reload-failure", "§c人格配置重载失败：");
        this.commandUnknownSubcommand = config.getString("commands.unknown-subcommand", "§c未知的子命令：");
        this.commandAvailableSubcommands = config.getString("commands.available-subcommands", "§e可用子命令：reload");
        this.cooldownWarning = config.getString("commands.cooldown-warning", "§c请等待 {seconds} 秒后再试！");
        this.pluginCommandCooldownWarning = config.getString("commands.plugin-command-cooldown-warning", "§c玩家 {player} 正在冷却中，请等待 {seconds} 秒后再试！");
        this.worldBannedHint = config.getString("commands.world-banned-hint", "§c当前世界禁止使用 {ai_name}！");

        // 插件命令专用消息
        this.pluginCommandPlayerBlocked = config.getString("plugins-command.player-blocked", "§c请使用 /kilacraft <消息>");
        this.pluginCommandInsufficientArgs = config.getString("plugins-command.insufficient-args", "§c参数不足！使用方法：/kilacraft plugins <人格> <内容> <玩家 UUID> [回调命令...]");
        this.pluginCommandUsageExample = config.getString("plugins-command.usage-example", "§e示例：/kilacraft plugins 严厉教师 你好 069a79f4-44e9-4726-a5be-fca90e38aaf5");
        this.pluginCommandCallbackHint = config.getString("plugins-command.callback-hint", "§7可选：在末尾添加回调命令，AI 完成后自动执行");
        this.pluginCommandCallbackExample = config.getString("plugins-command.callback-example", "§e带回调：/kilacraft plugins mm_ai 你好 UUID testai handleAI {response} mm_ai");
        this.pluginCommandCallbackPlaceholderHint = config.getString("plugins-command.callback-placeholder-hint", "§7{response} 会被替换为实际的 AI 回复内容");
        this.pluginCommandInvalidUuid = config.getString("plugins-command.invalid-uuid", "§c无效的玩家 UUID 格式：");
        this.pluginCommandUuidFormatHint = config.getString("plugins-command.uuid-format-hint", "§e请确保 UUID 格式正确，例如：069a79f4-44e9-4726-a5be-fca90e38aaf5");
        this.pluginCommandPersonalityNotFound = config.getString("plugins-command.personality-not-found", "§c未找到人格配置：");
        this.pluginCommandPersonalityListHint = config.getString("plugins-command.personality-list-hint", "§e可用的人格列表：");
        this.pluginCommandError = config.getString("plugins-command.error", "§c发生错误：");

        // 日志消息
        this.logConfigReloaded = config.getString("logs.config-reloaded", "配置已由 {sender} 重载");
        this.logKnowledgeReloaded = config.getString("logs.knowledge-reloaded", "知识库已由 {sender} 重载");
        this.logPersonalitiesReloaded = config.getString("logs.personalities-reloaded", "人格配置已由 {sender} 重载");
        this.logChatModeEntered = config.getString("logs.chat-mode-entered", "玩家 {player} 已进入连续对话模式");
        this.logChatModeExited = config.getString("logs.chat-mode-exited", "玩家 {player} 已退出连续对话模式");
        this.logClearSelfLogged = config.getString("logs.clear-self-logged", "玩家 {player} 已清除对话历史记录");
        this.logClearOtherLogged = config.getString("logs.clear-other-logged", "{sender} 已清除玩家 {player} 的对话历史记录");
        this.logPlayerCommandAttempt = config.getString("logs.player-command-attempt", "玩家 {player} 尝试执行控制台专用命令 /kilacraft plugins");
        this.logAiRequestError = config.getString("logs.ai-request-error", "AI 请求发生错误：");
        this.logConsoleAiError = config.getString("logs.console-ai-error", "控制台 AI 请求发生错误：");
        this.logPluginCommandAiError = config.getString("logs.plugin-command-ai-error", "插件命令 AI 请求发生错误：");
    }

    /**
     * 替换消息中的占位符
     *
     * @param message     原始消息
     * @param placeholder 占位符名称（不含花括号）
     * @param value       替换值
     * @return 替换后的消息
     */
    public String replacePlaceholder(String message, String placeholder, String value) {
        if (message == null || value == null) {
            return message;
        }
        return message.replace("{" + placeholder + "}", value);
    }

    /**
     * 替换多个占位符
     *
     * @param message      原始消息
     * @param placeholders 占位符和值的数组，格式为：{placeholder1, value1, placeholder2, value2, ...}
     * @return 替换后的消息
     */
    public String replacePlaceholders(String message, String... placeholders) {
        if (message == null || placeholders == null || placeholders.length % 2 != 0) {
            return message;
        }

        String result = message;
        for (int i = 0; i < placeholders.length; i += 2) {
            result = replacePlaceholder(result, placeholders[i], placeholders[i + 1]);
        }
        return result;
    }
}
