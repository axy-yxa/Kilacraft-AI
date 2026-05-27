package com.zm.kilacraftAI.service.notification;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.service.health.SparkDataCollector;

import java.util.List;

/**
 * 告警数据 → 标准通知消息的格式化器
 *
 * <p>从分析上下文构建 NotificationMessage，内容包含：
 * 告警触发原因、实时快照值、AI 诊断结论。</p>
 * <p>完整诊断报告（含服务端版本、Spark URL、玩家名等敏感信息）不推送，仅保留在服务器本地。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public final class NotificationMessageFormatter {

    private static final int MAX_AI_DIAGNOSIS_LENGTH = 1500;

    /** 推理过程折叠块正则（中文） */
    private static final String REASONING_PATTERN_ZH = "(?s)<details><summary>AI 推理过程</summary>.*?</details>\\s*";
    /** 推理过程折叠块正则（英文） */
    private static final String REASONING_PATTERN_EN = "(?s)<details><summary>AI Reasoning Process</summary>.*?</details>\\s*";

    private NotificationMessageFormatter() {
    }

    /**
     * 构建 auto 模式告警通知消息
     *
     * @param alerts      触发告警列表
     * @param snapshot    实时快照（可为 null）
     * @param aiDiagnosis AI 诊断结论全文（可为 null）
     * @return 标准通知消息
     */
    public static NotificationMessage buildAutoAlert(List<String> alerts, SparkDataCollector.HealthSnapshot snapshot, String aiDiagnosis) {
        String title = I18nService.tr("服务器健康状态异常告警");

        StringBuilder content = new StringBuilder();

        // 1. 触发告警
        content.append(I18nService.tr("触发告警:")).append("\n");
        if (alerts != null) {
            for (String alert : alerts) {
                content.append("- ").append(alert).append("\n");
            }
        }
        content.append("\n");

        // 2. 实时快照值
        if (snapshot != null) {
            content.append(I18nService.tr("实时指标:")).append("\n");
            content.append("TPS: ").append(formatDouble(snapshot.tps1m())).append(" | ");
            content.append("MSPT: median ").append(formatDouble(snapshot.msptMedian())).append("ms, ");
            content.append("max ").append(formatDouble(snapshot.msptMax())).append("ms | ");
            content.append("CPU: ").append(String.format("%.1f%%", snapshot.cpuProcess())).append("\n\n");
        }

        // 3. AI 诊断结论（剥离推理过程后截断）
        if (aiDiagnosis != null && !aiDiagnosis.isEmpty()) {
            // 剥离 <details> 推理过程折叠块（中英文），外部通知渠道不支持 HTML 折叠
            String stripped = aiDiagnosis.replaceAll(REASONING_PATTERN_ZH, "").replaceAll(REASONING_PATTERN_EN, "").trim();
            content.append(I18nService.tr("AI 诊断结论:")).append("\n");
            if (stripped.length() > MAX_AI_DIAGNOSIS_LENGTH) {
                content.append(stripped, 0, MAX_AI_DIAGNOSIS_LENGTH).append("...\n\n> ").append(I18nService.tr("查看完整诊断报告请登录服务器查看"));
            } else {
                content.append(stripped);
            }
            content.append("\n\n");
        }

        return NotificationMessage.of(title, content.toString());
    }

    /**
     * 构建测试通知消息
     */
    public static NotificationMessage buildTestMessage() {
        return NotificationMessage.of(I18nService.tr("Kilacraft-AI 通知测试"), I18nService.tr("这是一条测试通知，用于验证通知渠道配置是否正确。"));
    }

    private static String formatDouble(double value) {
        return String.format("%.1f", value);
    }


}
