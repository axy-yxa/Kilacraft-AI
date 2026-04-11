package com.zm.kilacraftAI.core;

import com.zm.kilacraftAI.enums.PluginPermissionEnum;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 补全
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:21:04
 */
public class TabCompleter implements org.bukkit.command.TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            // 第一级参数：所有子命令
            List<String> completions = new ArrayList<>();

            // reload 命令（需要权限）
            if (PluginPermissionEnum.RELOAD.hasPermission(sender)) {
                completions.add("reload");
            }

            // clear 命令（需要权限）
            if (PluginPermissionEnum.CLEAR_SELF.hasPermission(sender) || PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
                completions.add("clear");
            }

            // chat 命令（如果启用）
            completions.add("chat");

            // knowledge 命令（需要权限）
            if (PluginPermissionEnum.KNOWLEDGE.hasPermission(sender)) {
                completions.add("knowledge");
            }

            // personalities 命令（需要权限）
            if (PluginPermissionEnum.PERSONALITIES.hasPermission(sender)) {
                completions.add("personalities");
            }

            // afk 命令（需要权限）
            if (PluginPermissionEnum.AFK.hasPermission(sender)) {
                completions.add("afk");
            }

            return getCompletions(args[0], completions);
        }

        if (args.length == 2 && "knowledge".equals(args[0])) {
            // knowledge 的子命令
            List<String> completions = new ArrayList<>();

            if (PluginPermissionEnum.KNOWLEDGE.hasPermission(sender)) {
                completions.add("reload");
            }

            return getCompletions(args[1], completions);
        }

        if (args.length == 2 && "personalities".equals(args[0])) {
            // personalities 的子命令
            List<String> completions = new ArrayList<>();

            if (PluginPermissionEnum.PERSONALITIES.hasPermission(sender)) {
                completions.add("reload");
            }

            return getCompletions(args[1], completions);
        }

        if (args.length == 2 && "afk".equals(args[0])) {
            // afk 的子命令
            List<String> completions = new ArrayList<>();

            if (PluginPermissionEnum.AFK.hasPermission(sender)) {
                completions.add("query");
                completions.add("cancel");
            }

            return getCompletions(args[1], completions);
        }

        // 其他情况不补全
        return new ArrayList<>();
    }

    /**
     * 获取匹配的补全建议
     *
     * @param currentInput   当前输入的部分字符串
     * @param allCompletions 所有可能的补全选项
     * @return 匹配的补全列表
     */
    private List<String> getCompletions(String currentInput, List<String> allCompletions) {
        if (currentInput == null || currentInput.isEmpty()) {
            return allCompletions;
        }

        List<String> result = new ArrayList<>();
        String lowerInput = currentInput.toLowerCase();

        for (String completion : allCompletions) {
            if (completion.toLowerCase().startsWith(lowerInput)) {
                result.add(completion);
            }
        }

        return result;
    }
}