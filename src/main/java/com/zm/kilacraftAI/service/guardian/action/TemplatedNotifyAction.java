package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.output.AIResponsePipeline;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * L1 动作：模板通知。无歧义的告警走结构化模板，零 LLM 调用、不写 conversation。
 *
 * <p>模板支持 {@code {trigger_value}} 与 {@code {player}} 占位符，由 {@link GuardianContext#triggerValue()}
 * 填充。典型用例：「⚠ 苦力怕在你身后！」「熔炉烧好了」「还差 {trigger_value} 个铁锭」。
 * 经 {@link AIResponsePipeline#send} 路由到配置的载体（默认 CHAT，可 ACTION_BAR）。</p>
 *
 * <p>不写 conversation 历史（瞬态告警，无玩家交互信号）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class TemplatedNotifyAction implements GuardianAction {

    private final AIResponsePipeline pipeline;
    private final String template;

    public TemplatedNotifyAction(AIResponsePipeline pipeline, String template) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.template = Objects.requireNonNull(template, "template");
    }

    @Override
    public CompletableFuture<Outcome> perform(GuardianContext ctx) {
        Player player = ctx.player();
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(Outcome.PERMANENT_FAIL);
        }
        String msg = render(template, ctx);
        pipeline.send(player, msg, OutputScenarioEnum.GUARDIAN);
        return CompletableFuture.completedFuture(Outcome.SUCCESS);
    }

    /** 渲染占位符。triggerValue 可能为 empty（轮询型无触发值）。 */
    static String render(String template, GuardianContext ctx) {
        String s = template.replace("{player}", ctx.player().getName());
        String tv = ctx.triggerValue().map(Object::toString).orElse("");
        return s.replace("{trigger_value}", tv);
    }

    public String template() {
        return template;
    }
}
