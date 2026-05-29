package com.zm.kilacraftAI.metrics;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
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

        // 读取配置特征快照
        initFeatureFlags(plugin, collector);

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

        // Chart 6: 全量 Skill 元信息（用于全球 Skill 台账）
        metrics.addCustomChart(new SimplePie("all_skills", collector::getAllSkillsJson));

        // Chart 7: 数据库类型分布
        metrics.addCustomChart(new SimplePie("database_type", collector::getDatabaseType));

        // Chart 8: 流式输出使用率
        metrics.addCustomChart(new SimplePie("streaming_enabled", collector::getStreamingEnabled));

        // Chart 9: 默认输出载体分布
        metrics.addCustomChart(new SimplePie("output_channel", collector::getOutputChannel));

        // Chart 10: Embedding 模型分布
        metrics.addCustomChart(new SimplePie("embedding_model", collector::getEmbeddingModel));

        // Chart 11: 推理模型分布
        metrics.addCustomChart(new SimplePie("thinking_model", collector::getThinkingModel));

        PluginLoggerUtil.info("统计", "bStats 统计已启用");
    }

    /**
     * 从配置读取插件特征标志（启动时一次性快照）
     */
    private static void initFeatureFlags(KilacraftAI plugin, MetricsCollector collector) {
        // 数据库类型
        try {
            collector.setDatabaseType(plugin.getDatabaseConfigManager().getConfig().getType().name());
        } catch (Exception ignored) {
            collector.setDatabaseType("unknown");
        }

        // 流式输出
        collector.setStreamingEnabled(boolStr(plugin.getConfigManager().getOutputConfigManager().isStreamEnabled()));

        // Embedding
        boolean embeddingActuallyEnabled = plugin.getConfigManager().isEmbeddingEnabled() && isNotBlank(plugin.getConfigManager().getEmbeddingApiUrl()) && isNotBlank(plugin.getConfigManager().getEmbeddingApiKey()) && isNotBlank(plugin.getConfigManager().getEmbeddingModel());
        if (embeddingActuallyEnabled) {
            collector.setEmbeddingModel(plugin.getConfigManager().getEmbeddingModel());
        }

        // 推理模型
        if (plugin.getAdminConfigManager() != null) {
            boolean thinkingConfigured = plugin.getAdminConfigManager().isThinkingModelConfigured();
            if (thinkingConfigured) {
                collector.setThinkingModel(plugin.getAdminConfigManager().getThinkingModelConfig().model());
            }
        }

        // 输出载体
        try {
            collector.setOutputChannel(plugin.getConfigManager().getOutputConfigManager().getDefaultChannel().name());
        } catch (Exception ignored) {
            collector.setOutputChannel("unknown");
        }
    }

    /**
     * boolean → "true" / "false" 字符串
     */
    private static String boolStr(boolean value) {
        return value ? "true" : "false";
    }

    /**
     * 字符串非空且非空白
     */
    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
