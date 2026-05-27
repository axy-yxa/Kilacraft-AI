package com.zm.kilacraftAI.service.notification.channel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.common.enums.NotificationChannelTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.notification.NotificationChannel;
import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.service.notification.NotificationMessageFormatter;
import com.zm.kilacraftAI.model.notification.NotificationResult;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;

/**
 * Discord Webhook 通知渠道
 *
 * <p>支持 embed 消息格式。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public class DiscordWebhookChannel implements NotificationChannel {

    private static final String LOG_PREFIX = "通知";
    private static final int EMBED_COLOR = 0xFFAA00;
    private static final int MAX_EMBED_DESCRIPTION = 4096;

    private final String webhookUrl;

    public DiscordWebhookChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String type() {
        return NotificationChannelTypeEnum.DISCORD.getType();
    }

    @Override
    public NotificationResult send(OkHttpClient client, NotificationMessage message) {
        try {
            return sendEmbed(client, message);
        } catch (Exception e) {
            return NotificationResult.fail(I18nService.tr("发送失败: {}", e.getMessage()));
        }
    }

    @Override
    public NotificationResult testConnection(OkHttpClient client) {
        try {
            return sendEmbed(client, NotificationMessageFormatter.buildTestMessage());
        } catch (Exception e) {
            return NotificationResult.fail(I18nService.tr("测试失败: {}", e.getMessage()));
        }
    }

    @Override
    public String getMaskedUrl() {
        if (webhookUrl == null || webhookUrl.length() < 40) return "***";
        return webhookUrl.substring(0, 35) + "***";
    }

    /**
     * 发送 embed 消息
     */
    private NotificationResult sendEmbed(OkHttpClient client, NotificationMessage message) throws IOException {
        JsonObject body = buildPayload(buildEmbed(message));
        Request request = new Request.Builder().url(webhookUrl)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build();

        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response);
        }
    }

    /**
     * 构建 Discord embed JSON 对象
     */
    private JsonObject buildEmbed(NotificationMessage message) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", message.title());
        embed.addProperty("description", truncateContent(message.content(), MAX_EMBED_DESCRIPTION));
        embed.addProperty("color", EMBED_COLOR);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Kilacraft-AI Health Guardian");
        embed.add("footer", footer);
        return embed;
    }

    /**
     * 构建 embeds payload
     */
    private JsonObject buildPayload(JsonObject embed) {
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        return body;
    }

    private NotificationResult handleResponse(Response response) throws IOException {
        if (response.isSuccessful()) {
            return NotificationResult.ok(I18nService.tr("发送成功"));
        }
        String errorBody = response.body() != null ? response.body().string() : "unknown";
        PluginLoggerUtil.warn(LOG_PREFIX, "[Discord] HTTP {} - {}", response.code(), errorBody);
        return NotificationResult.fail("HTTP " + response.code());
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }
}
