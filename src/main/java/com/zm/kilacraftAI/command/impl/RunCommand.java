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
import com.zm.kilacraftAI.skills.framework.*;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * /kila run <技能名> <提示词>：强制指定技能执行（绕过 Phase1 分类）。
 * 权限由目标技能自身权限约束（经 SkillManager.getAvailableSkills 校验，与聊天触发同源）。
 * 流程：技能可用校验 → SkillIntentRecognizer.recognizeForcedSkill 提取动作与参数 →
 * 单意图走 SkillManager.executeSkillByIntent；多步骤任务走 TaskExecutor（依赖排序/占位符解析/步骤失败不中断）。
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
        recognizer.recognizeForcedSkill(prompt, history, player.getName(), player, skillName).thenCompose(result -> {
            if (result == null) {
                // 意图识别失败（缺参数/无法解析）：降级普通 AI，带上失败上下文让 AI 合理回应
                // （询问必要参数 / 直接满足需求），而不是死胡同提示"换种说法"
                PluginLoggerUtil.debug("命令", "强制技能 {} 未解析出意图，降级普通 AI", skillName);
                boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
                String enriched = prompt + "\n" + I18nService.tr("[系统提示：玩家通过 /kila run {} 指定技能执行，但未能解析出可执行意图。请根据玩家原始需求协助，如需必要参数请直接询问。]", skillName);
                new AIRequestHandler(plugin).handleAIRequest(player, enriched, history, enableAgent, false, ConversationSourceEnum.COMMAND);
                return CompletableFuture.completedFuture(null);
            }
            if (result instanceof SkillIntent intent) {
                // 单意图：直接执行
                SkillContext context = new SkillContext(player, intent.getAction(), intent.getEntities()).withAudit(prompt, "manual_run");
                return skillManager.executeSkillByIntent(intent, context).thenApply(r -> (Object) r);
            }
            // 多步骤任务：复用 TaskExecutor 走完整管线（依赖排序/占位符解析/步骤失败不中断）
            TaskPlan plan = (TaskPlan) result;
            SkillContext context = new SkillContext(player, null, new HashMap<>()).withAudit(prompt, "manual_run");
            return new TaskExecutor(skillManager).executeTask(plan, context, history, prompt).thenApply(r -> (Object) r);
        }).thenAccept(result -> {
            if (result != null) {
                FoliaCompat.runTask(plugin, () -> player.sendMessage(MessageUtil.getAIPrefix() + formatResult(result, lm)));
            }
        }).exceptionally(ex -> {
            FoliaCompat.runTask(plugin, () -> player.sendMessage(MessageUtil.getAIPrefix() + lm.getCommandRunError()));
            return null;
        });
    }

    /**
     * 格式化执行结果：单意图取 SkillResult 原文；多步骤任务按步骤逐条展示。
     */
    private static String formatResult(Object result, LanguageManager lm) {
        if (result instanceof SkillResult sr) {
            return sr.isSuccess() ? sr.getMessage() : "§c" + sr.getMessage();
        }
        AnalysisSummary summary = (AnalysisSummary) result;
        StringBuilder sb = new StringBuilder();
        for (AnalysisSummary.StepResult step : summary.getResults()) {
            boolean ok = "SUCCESS".equals(step.status());
            sb.append(ok ? "§a" : "§c").append("• ").append(step.message()).append("\n");
        }
        String text = sb.toString();
        return text.isEmpty() ? lm.getCommandRunTaskEmpty() : text.stripTrailing();
    }
}
