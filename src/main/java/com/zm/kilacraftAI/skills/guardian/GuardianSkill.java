package com.zm.kilacraftAI.skills.guardian;

import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.service.guardian.AlertCategory;
import com.zm.kilacraftAI.service.guardian.GuardianManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 守护系统自然语言入口。支持 enable/disable/status/silence 四个动作（见 {@link #getHints()}）。
 * 仅做开关/静音，不创建 monitor。
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public class GuardianSkill implements Skill {

    private final GuardianManager guardianManager;

    public GuardianSkill(GuardianManager guardianManager) {
        this.guardianManager = guardianManager;
    }

    @Override
    public String getName() {
        return "Guardian";
    }

    @Override
    public String getDescription() {
        return "守护系统：开启后 AI 会主动提醒背后视野外的威胁（当前仅此一类，不支持自定义监控目标如熔炉/农作物）。"
                + "可开启/关闭/查询状态/静音分类。";
    }

    @Override
    public Map<String, String> getActions() {
        Map<String, String> actions = new HashMap<>();
        actions.put("enable", "开启守护（AI 主动提醒视野外威胁、远端状态等）");
        actions.put("disable", "关闭守护");
        actions.put("status", "查询守护当前状态");
        actions.put("silence", "静音某分类提醒（category: DANGER/RESOURCE/GOAL/COMPANION/GENERAL）");
        return actions;
    }

    @Override
    public List<String> getHints() {
        return List.of(
                "玩家说「帮我开守护/盯着我/当保镖」→ enable",
                "玩家说「关掉守护/不用了」→ disable",
                "玩家说「别提醒我战斗/安静点」→ silence（category=DANGER）",
                "玩家说「守护开着吗」→ status"
        );
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.GUARDIAN.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        // 全局开关关闭时，skill 不出现在意图识别提示词
        return guardianManager != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        // 守护仅服务在线玩家，所有动作统一校验
        org.bukkit.entity.Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("守护仅玩家可用"));
        }
        return CompletableFuture.completedFuture(switch (action) {
            case "enable" -> {
                if (guardianManager.isGuardianEnabled(player.getUniqueId())) {
                    yield SkillResult.success("守护已经开启了。");
                }
                Optional<List<String>> result = guardianManager.enable(player);
                if (result.isPresent()) {
                    yield SkillResult.success("守护已开启，我会盯着你看不见的地方。");
                }
                yield SkillResult.failure("守护系统已被服主关闭，无法开启。");
            }
            case "disable" -> {
                guardianManager.disable(context.getPlayer().getUniqueId());
                yield SkillResult.success("守护已关闭，我不会再主动提醒了。");
            }
            case "status" -> {
                boolean on = guardianManager.isGuardianEnabled(context.getPlayer().getUniqueId());
                yield SkillResult.success(on ? "守护当前已开启。" : "守护当前未开启。");
            }
            case "silence" -> {
                String cat = context.getEntities().get("category");
                if (cat == null || cat.isBlank()) {
                    yield SkillResult.needInfo("想静音哪类提醒？可选：DANGER（危险）、RESOURCE（资源）、GOAL（目标）、COMPANION（陪伴）、GENERAL（通用）。");
                }
                try {
                    AlertCategory c = AlertCategory.valueOf(cat.toUpperCase());
                    guardianManager.silence(context.getPlayer().getUniqueId(), c);
                    yield SkillResult.success("好的，我会静音 " + c.name() + " 分类的提醒。");
                } catch (IllegalArgumentException e) {
                    yield SkillResult.needInfo("无效分类，可选：DANGER、RESOURCE、GOAL、COMPANION、GENERAL。");
                }
            }
            default -> SkillResult.failure("未知动作: " + action);
        });
    }
}
