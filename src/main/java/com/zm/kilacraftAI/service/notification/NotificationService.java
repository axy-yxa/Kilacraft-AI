package com.zm.kilacraftAI.service.notification;

import com.zm.kilacraftAI.common.enums.NotificationChannelTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.model.notification.NotificationResult;
import com.zm.kilacraftAI.service.notification.channel.DingTalkWebhookChannel;
import com.zm.kilacraftAI.service.notification.channel.DiscordWebhookChannel;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 外部通知服务
 *
 * <p>管理所有已配置的通知渠道，提供统一的通知入口。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public class NotificationService {

    private static final String LOG_PREFIX = "通知";

    private volatile List<NotificationChannel> channels;
    private volatile OkHttpClient httpClient;
    @Getter
    private volatile boolean enabled;

    public NotificationService() {
        this.channels = Collections.emptyList();
        this.enabled = false;
    }

    /**
     * 从配置重新加载通知渠道
     *
     * @param enabled        是否启用通知
     * @param channelConfigs 渠道配置列表，每个元素为 [type, webhookUrl, secret]
     */
    public void reload(boolean enabled, List<ChannelConfig> channelConfigs) {
        this.enabled = enabled;

        // 关闭旧 HTTP 客户端
        OkHttpClient oldClient = this.httpClient;

        if (!enabled || channelConfigs == null || channelConfigs.isEmpty()) {
            this.channels = Collections.emptyList();
            this.httpClient = null;
            // 清理旧客户端
            if (oldClient != null) {
                oldClient.dispatcher().executorService().shutdown();
                oldClient.dispatcher().cancelAll();
                oldClient.connectionPool().evictAll();
            }

            PluginLoggerUtil.info(LOG_PREFIX, I18nService.tr("通知服务已禁用"));
            return;
        }

        // 构建 HTTP 客户端：短超时，适合 webhook 请求
        this.httpClient = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();

        // 构建渠道列表
        List<NotificationChannel> newChannels = new ArrayList<>();
        for (ChannelConfig config : channelConfigs) {
            NotificationChannel channel = createChannel(config);
            if (channel != null) {
                newChannels.add(channel);
                PluginLoggerUtil.info(LOG_PREFIX, I18nService.tr("已加载通知渠道: {} ({})", channel.type(), channel.getMaskedUrl()));
            }
        }
        this.channels = Collections.unmodifiableList(newChannels);

        // 清理旧客户端
        if (oldClient != null) {
            oldClient.dispatcher().executorService().shutdown();
            oldClient.dispatcher().cancelAll();
            oldClient.connectionPool().evictAll();
        }

        PluginLoggerUtil.info(LOG_PREFIX, I18nService.tr("通知服务已启用，已加载 {} 个渠道"), newChannels.size());
    }

    /**
     * 异步发送通知到所有已配置的渠道（调用者不等待结果）
     */
    public void notify(NotificationMessage message) {
        if (!enabled || channels.isEmpty()) return;

        List<NotificationChannel> snapshot = this.channels;
        OkHttpClient client = this.httpClient;
        if (client == null) return;

        for (NotificationChannel channel : snapshot) {
            FoliaCompat.getIOPool().execute(() -> {
                try {
                    NotificationResult result = channel.send(client, message);
                    if (!result.success()) {
                        PluginLoggerUtil.warn(LOG_PREFIX, "[{}] {}", channel.type(), result.message());
                    }
                } catch (Exception e) {
                    PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("[{}] 发送异常: {}", channel.type(), e.getMessage()));
                }
            });
        }
    }

    /**
     * 测试所有已配置的通知渠道（同步阻塞）
     *
     * @return 各渠道的测试结果
     */
    public List<ChannelTestResult> testAllChannels() {
        if (!enabled || channels.isEmpty() || httpClient == null) {
            return Collections.emptyList();
        }

        List<ChannelTestResult> results = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            try {
                NotificationResult result = channel.testConnection(httpClient);
                results.add(new ChannelTestResult(channel.type(), result));
            } catch (Exception e) {
                results.add(new ChannelTestResult(channel.type(), NotificationResult.fail(e.getMessage())));
            }
        }
        return results;
    }

    /**
     * 通知是否启用且渠道已配置
     */
    public boolean isReady() {
        return enabled && !channels.isEmpty() && httpClient != null;
    }

    public int getChannelCount() {
        return channels.size();
    }

    /**
     * 关闭通知服务，释放 HTTP 客户端资源
     */
    public void shutdown() {
        this.enabled = false;
        this.channels = Collections.emptyList();
        OkHttpClient client = this.httpClient;
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
        this.httpClient = null;
    }

    private NotificationChannel createChannel(ChannelConfig config) {
        NotificationChannelTypeEnum type = NotificationChannelTypeEnum.fromType(config.type());
        if (type == null) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("未知通知渠道类型: {}", config.type()));
            return null;
        }
        if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("{} 渠道 webhook_url 未配置，跳过", type.getDisplayName()));
            return null;
        }
        return switch (type) {
            case DISCORD -> new DiscordWebhookChannel(config.webhookUrl());
            case DINGTALK -> new DingTalkWebhookChannel(config.webhookUrl(), config.secret());
        };
    }

    /**
     * 渠道配置
     */
    public record ChannelConfig(String type, String webhookUrl, String secret) {
    }

    /**
     * 渠道测试结果
     */
    public record ChannelTestResult(String type, NotificationResult result) {
    }
}
