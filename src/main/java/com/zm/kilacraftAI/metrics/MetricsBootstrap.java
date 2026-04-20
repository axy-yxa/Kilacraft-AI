package com.zm.kilacraftAI.metrics;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;

/**
 * bStats 统计引导器
 *
 * <p>在插件 onEnable 时调用 {@link #bootstrap}，完成 bStats 初始化和 Chart 注册。</p>
 * <p>所有统计数据由 {@link MetricsCollector} 提供，本类只负责桥接 bStats API。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-18
 */
public class MetricsBootstrap {

    private static final int BSTATS_PLUGIN_ID = 30853;

    /**
     * 初始化 bStats 统计
     *
     * @param plugin 插件实例
     */
    public static void bootstrap(KilacraftAI plugin) {
        if (!plugin.getConfig().getBoolean("metrics", true)) {
            return;
        }

        MetricsCollector collector = MetricsCollector.getInstance();

        // 初始化静态数据
        collector.setLlmModel(plugin.getConfigManager().getLlmModel());

        // 注册 bStats
        Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);

        // Chart 1: 技能调用次数 TOP 10（含第三方 SPI Skill）
        metrics.addCustomChart(new AdvancedPie("skill_action_usage", collector::getSkillActionSnapshot));

        // Chart 2: 请求类型分布
        metrics.addCustomChart(new AdvancedPie("request_type", collector::getRequestTypeSnapshot));

        // Chart 3: 挂机任务类型分布
        metrics.addCustomChart(new AdvancedPie("afk_task_type", collector::getAfkTaskTypeSnapshot));

        // Chart 4: LLM 模型分布
        metrics.addCustomChart(new SimplePie("llm_model", collector::getLlmModel));

        // Chart 5: Skill 来源分布
        metrics.addCustomChart(new AdvancedPie("skill_source", collector::getSkillSourceSnapshot));

        PluginLogger.info("统计", "bStats 统计已启用");
    }
}
