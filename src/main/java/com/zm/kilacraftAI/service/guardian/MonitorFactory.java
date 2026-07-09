package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.GuardianConfigManager.MonitorTemplate;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.action.GuardianAction;
import com.zm.kilacraftAI.service.guardian.action.GuardianLlmAction;
import com.zm.kilacraftAI.service.guardian.action.SkillAction;
import com.zm.kilacraftAI.service.guardian.action.TemplatedNotifyAction;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import com.zm.kilacraftAI.service.guardian.monitor.PollingTriggerSource;
import com.zm.kilacraftAI.service.guardian.monitor.TriggerSource;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;
import com.zm.kilacraftAI.service.guardian.predicate.PredicateRegistry;
import com.zm.kilacraftAI.service.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;

import java.util.Optional;

/**
 * 把 {@link MonitorTemplate} 实例化为 {@link Monitor}（自然语言入口 + 配置驱动的建监控单元装配）。
 *
 * <p>装配三要素：谓词（PredicateRegistry 查 Factory.create）、动作（按 actionType 选实现）、
 * 触发源（模板默认用 PollingTriggerSource）。
 * 任何一环失败返回 empty（GuardianManager 跳过该模板并记 warn，不影响其他模板）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class MonitorFactory {

    private final PredicateRegistry predicateRegistry;
    private final AIResponsePipeline pipeline;
    private final SkillManager skillManager;
    private final LLMOutputCoordinator coordinator;

    public MonitorFactory(PredicateRegistry predicateRegistry, AIResponsePipeline pipeline,
                          SkillManager skillManager, LLMOutputCoordinator coordinator) {
        this.predicateRegistry = predicateRegistry;
        this.pipeline = pipeline;
        this.skillManager = skillManager;
        this.coordinator = coordinator;
    }

    /**
     * 从模板创建 monitor。id 为模板名（同一玩家内唯一）。
     *
     * @param playerSpecificPos 玩家配置覆盖的谓词参数（如熔炉坐标），可为空
     */
    public Optional<Monitor> create(MonitorTemplate tmpl, java.util.Map<String, String> playerSpecificPos) {
        // 1. 谓词
        Optional<PredicateRegistry.Descriptor> desc = predicateRegistry.get(tmpl.predicateType());
        if (desc.isEmpty()) {
            PluginLoggerUtil.warn("守护系统", I18nService.tr("模板 {} 的谓词 {} 未注册，跳过", tmpl.name(), tmpl.predicateType()));
            return Optional.empty();
        }
        java.util.Map<String, String> mergedParams = new java.util.HashMap<>(tmpl.predicateParams());
        if (playerSpecificPos != null) {
            mergedParams.putAll(playerSpecificPos);
        }
        Predicate predicate;
        try {
            predicate = desc.get().factory().create(mergedParams);
        } catch (Exception e) {
            PluginLoggerUtil.warn("守护系统", I18nService.tr("模板 {} 谓词构造失败: {}", tmpl.name(), e.getMessage()));
            return Optional.empty();
        }

        // 2. 动作
        GuardianAction action = createAction(tmpl);
        if (action == null) {
            return Optional.empty();
        }

        // 3. 触发源（模板默认用 PollingTriggerSource）
        TriggerSource source = new PollingTriggerSource(tmpl.cadenceTicks());

        // 4. 装配 Monitor（cooldownMillis > 0 时覆盖默认值，用于 CRITICAL 类持续告警的去抖）
        var builder = Monitor.builder(tmpl.name(), source, action, tmpl.policy())
                .trigger(predicate)
                .category(tmpl.category())
                .priority(tmpl.priority());
        if (tmpl.cooldownMillis() > 0) {
            builder.cooldownMillis(tmpl.cooldownMillis());
        }
        Monitor m = builder.build();
        return Optional.of(m);
    }

    private GuardianAction createAction(MonitorTemplate tmpl) {
        return switch (tmpl.actionType()) {
            case "template" -> new TemplatedNotifyAction(pipeline, tmpl.actionTemplate());
            case "skill" -> {
                if (tmpl.skillName().isBlank()) {
                    PluginLoggerUtil.warn("守护系统", I18nService.tr("模板 {} 的 action.type=skill 但 skill 名为空", tmpl.name()));
                    yield null;
                }
                yield new SkillAction(skillManager, tmpl.skillName(), tmpl.skillAction(), tmpl.skillEntities());
            }
            case "llm" -> new GuardianLlmAction(coordinator);
            default -> {
                PluginLoggerUtil.warn("守护系统", I18nService.tr("模板 {} 未知 action.type: {}", tmpl.name(), tmpl.actionType()));
                yield null;
            }
        };
    }
}
