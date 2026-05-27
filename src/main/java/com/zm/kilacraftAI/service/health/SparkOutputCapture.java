package com.zm.kilacraftAI.service.health;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Spark Profiler 输出捕获器
 *
 * <p>通过 log4j2 Appender 拦截 Spark profiler 命令的控制台输出，
 * 从中提取 Profiler viewer URL。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class SparkOutputCapture {

    private static final String LOG_PREFIX = "健康监控";
    /**
     * Spark viewer URL 正则
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https://spark\\.lucko\\.me/[a-zA-Z0-9]+");

    private volatile String profilerUrl;
    private final CountDownLatch latch = new CountDownLatch(1);
    private Appender appender;
    /**
     * 递归调用检测：防止 Appender 输出的日志被自身再次捕获（实例变量，避免多实例干扰）
     */
    private final AtomicBoolean inAppend = new AtomicBoolean(false);
    /**
     * URL 已捕获标记，捕获后立即停止拦截，避免 Spark 后续日志被我们的 appender 处理
     */
    private volatile boolean captured = false;

    /**
     * 安装日志捕获器，开始拦截控制台输出中的 Spark URL
     */
    public void startCapture() {
        @SuppressWarnings("deprecation") AbstractAppender newAppender = new AbstractAppender("KilacraftAI-SparkCapture-" + System.identityHashCode(this), null, null, true) {
            @Override
            public void append(LogEvent event) {
                // URL 已捕获，不再处理任何日志
                if (captured) return;
                // 防止递归调用
                if (!inAppend.compareAndSet(false, true)) {
                    return;
                }
                try {
                    String msg = event.getMessage().getFormattedMessage();
                    if (msg != null) {
                        var matcher = URL_PATTERN.matcher(msg);
                        if (matcher.find()) {
                            profilerUrl = matcher.group();
                            // 标记已捕获，后续日志直接跳过
                            captured = true;
                            latch.countDown();
                        }
                    }
                } finally {
                    inAppend.set(false);
                }
            }
        };
        this.appender = newAppender;
        newAppender.start();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        config.addLoggerAppender(context.getRootLogger(), appender);
        context.updateLoggers();
    }

    /**
     * 移除日志捕获器
     */
    public void stopCapture() {
        if (appender != null) {
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                Configuration config = context.getConfiguration();
                config.getRootLogger().removeAppender(appender.getName());
                context.updateLoggers();
                appender.stop();
            } catch (Exception e) {
                PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("移除日志捕获器失败: {}", e.getMessage()), e);
            }
        }
    }

    /**
     * 等待 Profiler URL（阻塞，带超时）
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return URL 字符串，超时或中断返回 null
     */
    public String awaitUrl(long timeout, TimeUnit unit) {
        try {
            if (latch.await(timeout, unit)) {
                return profilerUrl;
            }
            PluginLoggerUtil.warn(LOG_PREFIX, "等待 Profiler URL 超时（{} {}）", timeout, unit);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
