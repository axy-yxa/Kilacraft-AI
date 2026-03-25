package com.zm.kilacraftAI.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tab补全
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:21:04
 */
public class TabCompleter implements org.bukkit.command.TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("kilacraft.reload")) {
                return List.of("reload");
            }
        }
        return new ArrayList<>();
    }
}