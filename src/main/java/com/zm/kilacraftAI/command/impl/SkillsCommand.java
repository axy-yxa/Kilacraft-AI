package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /kila skills：列出调用者有权使用的技能（与意图识别权限预过滤同源）。
 * 技能集合来自 SkillManager.getAvailableSkills(Player)，玩家只看到自己能触发的技能。分页展示。
 *
 * @author Zm_Mmm
 * @since 2026-06-25
 */
public final class SkillsCommand {

    private static final int PAGE_SIZE = 8;

    private SkillsCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        SkillManager skillManager = plugin.getSkillManager();
        if (skillManager == null) {
            sender.sendMessage(lm.getCommandSkillsNotInit());
            return;
        }

        if (!(sender instanceof Player caller)) {
            sender.sendMessage(lm.getCommandRunPlayerOnly());
            return;
        }
        List<Skill> skills = skillManager.getAvailableSkills(caller);
        if (skills.isEmpty()) {
            sender.sendMessage(lm.getCommandSkillsEmpty());
            return;
        }

        int page = parsePage(args.length > 1 ? args[1] : null);
        int totalPages = (skills.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, skills.size());

        sender.sendMessage(lm.replacePlaceholders(lm.getCommandSkillsTitle(), "page", String.valueOf(page), "total", String.valueOf(totalPages)));
        for (int i = from; i < to; i++) {
            Skill skill = skills.get(i);
            String desc = skill.getDescription();
            String thirdPartyTag = SkillManager.isThirdPartySkill(skill) ? " §7(第三方)" : "";
            sender.sendMessage("§b" + skill.getName() + thirdPartyTag + "§7 - §f" + (desc != null ? desc : ""));
        }
        if (totalPages > 1) {
            sender.sendMessage(lm.replacePlaceholders(lm.getCommandSkillsPagination(), "total", String.valueOf(totalPages)));
        }
    }

    public static int parsePage(String arg) {
        if (arg == null || arg.isEmpty()) return 1;
        try {
            return Math.max(1, Integer.parseInt(arg));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
