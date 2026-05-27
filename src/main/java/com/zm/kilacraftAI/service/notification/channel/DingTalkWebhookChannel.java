package com.zm.kilacraftAI.service.notification.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.common.enums.NotificationChannelTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.notification.NotificationChannel;
import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.service.notification.NotificationMessageFormatter;
import com.zm.kilacraftAI.model.notification.NotificationResult;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钉钉群机器人 Webhook 通知渠道
 *
 * <p>仅支持 Markdown 消息，不支持文件上传。可选 HMAC-SHA256 签名安全加固。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-25
 */
public class DingTalkWebhookChannel implements NotificationChannel {

    private static final String LOG_PREFIX = "通知";
    private static final int MARKDOWN_MAX_TEXT = 4000;
    private static final Gson GSON = new Gson();

    private final String webhookUrl;
    private final String secret;

    public DingTalkWebhookChannel(String webhookUrl, String secret) {
        this.webhookUrl = webhookUrl;
        this.secret = (secret != null && !secret.isBlank()) ? secret : null;
    }

    @Override
    public String type() {
        return NotificationChannelTypeEnum.DINGTALK.getType();
    }

    @Override
    public NotificationResult send(OkHttpClient client, NotificationMessage message) {
        try {
            String url = buildSignedUrl();

            String markdownText = "### " + message.title() + "\n\n" + message.content();
            markdownText = truncateContent(markdownText, MARKDOWN_MAX_TEXT);

            JsonObject markdown = new JsonObject();
            markdown.addProperty("title", message.title());
            markdown.addProperty("text", markdownText);

            JsonObject body = new JsonObject();
            body.addProperty("msgtype", "markdown");
            body.add("markdown", markdown);

            RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).post(requestBody).build();

            try (Response response = client.newCall(request).execute()) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            return NotificationResult.fail(I18nService.tr("发送失败: {}", e.getMessage()));
        }
    }

    @Override
    public NotificationResult testConnection(OkHttpClient client) {
        try {
            return send(client, NotificationMessageFormatter.buildTestMessage());
        } catch (Exception e) {
            return NotificationResult.fail(I18nService.tr("测试失败: {}", e.getMessage()));
        }
    }

    @Override
    public String getMaskedUrl() {
        if (webhookUrl == null || webhookUrl.length() < 50) return "***";
        return webhookUrl.substring(0, 45) + "***";
    }

    /**
     * 构建带签名的 URL（secret 不为空时启用 HMAC-SHA256 签名）
     */
    private String buildSignedUrl() throws Exception {
        if (secret == null) {
            return webhookUrl;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = URLEncoder.encode(
                Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8))),
                StandardCharsets.UTF_8);

        String separator = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }

    private NotificationResult handleResponse(Response response) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";
        if (response.isSuccessful()) {
            try {
                JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
                if (json.has("errcode") && json.get("errcode").getAsInt() == 0) {
                    return NotificationResult.ok(I18nService.tr("发送成功"));
                }
            } catch (Exception ignored) {
            }
            PluginLoggerUtil.warn(LOG_PREFIX, "[钉钉] 响应异常: {}", responseBody);
            return NotificationResult.fail(responseBody);
        }
        PluginLoggerUtil.warn(LOG_PREFIX, "[钉钉] HTTP {} - {}", response.code(), responseBody);
        return NotificationResult.fail("HTTP " + response.code());
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }
}
