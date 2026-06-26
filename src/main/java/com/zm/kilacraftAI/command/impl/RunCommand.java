package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Deque;

/**
 * /kila run <技能名> <提示词>：强制指定技能执行（绕过 Phase1 分类）。
 * 权限由目标技能自身权限约束（经 SkillManager.getAvailableSkills 校验，与聊天触发同源）。
 * 流程：技能可用校验 → SkillIntentRecognizer.recognizeForcedSkill 提取动作与参数 →
 * 识别结果交 AIRequestHandler.handleForcedSkillResult，复用正常流程的"执行 + AI 二次总结 / 失败回退"，与聊天触发完全一致。
 * 意图识别失败（缺参数/无法解析）时降级普通 AI（带失败上下文），由 AI 合理回应，避免死胡同。仅限玩家使用。
 */
public final class RunCommand {

    private RunCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lm.getCommandRunPlayerOnly());
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lm.getCommandRunUsage());
            return;
        }

        String skillName = args[1];
        String prompt = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        SkillManager skillManager = plugin.getSkillManager();
        SkillIntentRecognizer recognizer = plugin.getIntentRecognizer();
        if (skillManager == null || recognizer == null) {
            sender.sendMessage(lm.getCommandRunNotInit());
            return;
        }

        // 同步预检：技能存在且玩家有权使用（即时反馈，与 skills 列举同源）
        boolean permitted = false;
        for (Skill skill : skillManager.getAvailableSkills(player)) {
            if (skillName.equals(skill.getName())) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            sender.sendMessage(lm.replacePlaceholders(lm.getCommandRunSkillNotFound(), "skill", skillName));
            return;
        }

        player.sendMessage(MessageUtil.getAIPrefix() + lm.replacePlaceholders(lm.getCommandRunExecuting(), "skill", skillName));

        Deque<ConversationManager.Message> history = plugin.getConversationManager().getOrCreateHistory(player.getUniqueId());
        AIRequestHandler handler = new AIRequestHandler(plugin);
        recognizer.recognizeForcedSkill(prompt, history, player.getName(), player, skillName).thenAccept(result -> {
            if (result == null) {
                // 意图识别失败（缺参数/无法解析）：降级普通 AI，带上失败上下文让 AI 合理回应
                // （询问必要参数 / 直接满足需求），而不是死胡同提示"换种说法"
                PluginLoggerUtil.debug("命令", "强制技能 {} 未解析出意图，降级普通 AI", skillName);
                boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
                String enriched = prompt + "\n" + I18nService.tr("[系统提示：玩家通过 /kila run {} 指定技能执行，但未能解析出可执行意图。请根据玩家原始需求协助，如需必要参数请直接询问。]", skillName);
                handler.handleAIRequest(player, enriched, history, enableAgent, false, ConversationSourceEnum.COMMAND);
                return;
            }
            // 复用正常流程：执行 + AI 二次总结（成功）/ 回退普通 AI（失败），与聊天触发完全一致
            handler.handleForcedSkillResult(player, result, prompt, history, ConversationSourceEnum.COMMAND);
        }).exceptionally(ex -> {
            FoliaCompat.runTask(plugin, () -> player.sendMessage(MessageUtil.getAIPrefix() + lm.getCommandRunError()));
            return null;
        });
    }
}
