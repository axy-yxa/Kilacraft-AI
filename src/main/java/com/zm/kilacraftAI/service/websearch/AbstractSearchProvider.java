package com.zm.kilacraftAI.service.websearch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.exception.SearchException;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.ThinkingModelCapable;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 搜索供应商抽象基类，封装共享的 HTTP 客户端获取与异步执行逻辑。
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public abstract class AbstractSearchProvider implements SearchProvider {

    /**
     * 时间范围常量
     */
    protected static final String RECENCY_DAY = "day";
    protected static final String RECENCY_WEEK = "week";
    protected static final String RECENCY_MONTH = "month";
    protected static final String RECENCY_YEAR = "year";

    protected static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    protected final KilacraftAI plugin;

    protected AbstractSearchProvider(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 构造供应商特定的 HTTP 请求
     *
     * @param query   搜索关键词
     * @param count   期望返回条数
     * @param recency 时间范围——day / week / month / year，null 表示不限
     * @return OkHttp Request
     */
    protected abstract Request buildRequest(String query, int count, String recency);

    /**
     * 解析供应商响应 JSON 为统一搜索结果列表
     *
     * @param responseJson 响应体 JSON
     * @param maxCount     最大条数
     * @return 搜索结果列表
     */
    protected abstract List<SearchResult> parseResults(JsonObject responseJson, int maxCount);

    @Override
    public CompletableFuture<List<SearchResult>> search(String query, int count, String recency) {
        return CompletableFuture.supplyAsync(() -> {
            OkHttpClient client = getHttpClient();
            if (client == null) {
                throw new SearchException(I18nService.tr("HTTP 客户端不可用，无法执行搜索"));
            }
            Request request = buildRequest(query, count, recency);
            try (Response response = client.newCall(request).execute()) {
                String body = readBody(response);
                if (!response.isSuccessful()) {
                    String errDetail = extractErrorDetail(body);
                    // 完整错误（含上游 errDetail）进日志，便于排查；上游错误体可能回显
                    // 请求头/内部端点，不能进入对话历史，故异常 message 仅含 HTTP code（脱敏）。
                    PluginLoggerUtil.warn("网页搜索", I18nService.tr("{} 搜索失败，HTTP {} {}", getProviderName(), response.code(), errDetail));
                    throw new SearchException(I18nService.tr("{} 搜索失败，HTTP {}", getProviderName(), response.code()));
                }
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                List<SearchResult> results = parseResults(json, count);
                PluginLoggerUtil.info("网页搜索", I18nService.tr("{} 搜索 '{}' 返回 {} 条结果", getProviderName(), query, results.size()));
                return results;
            } catch (IOException e) {
                PluginLoggerUtil.error("网页搜索", I18nService.tr("{} 搜索网络异常: {}", getProviderName(), e.getMessage()), e);
                throw new SearchException(I18nService.tr("{} 搜索失败，网络异常", getProviderName()), e);
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 读取响应体，空容错
     */
    private String readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    /**
     * 尝试从错误响应中提取 JSON error.message，回退为截断的 body 原文
     */
    private String extractErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                JsonObject err = json.getAsJsonObject("error");
                String msg = getJsonString(err, "message");
                if (!msg.isEmpty()) {
                    return "(" + msg + ")";
                }
            }
        } catch (Exception ignored) {
        }
        // 回退：截取前 200 字符方便排查
        String trimmed = body.trim();
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200) + "...";
        }
        return "[" + trimmed + "]";
    }

    /**
     * 获取共享 HTTP 客户端（复用 LLM Provider 的连接池）
     */
    protected OkHttpClient getHttpClient() {
        if (plugin.getLlmManager() != null && plugin.getLlmManager().getCurrentProvider() instanceof ThinkingModelCapable capable) {
            return capable.getSharedHttpClient();
        }
        return null;
    }

    /**
     * 辅助：安全提取 JSON 字符串字段
     */
    protected static String getJsonString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    /**
     * 辅助：安全提取 JSON 数组
     */
    protected static JsonArray getJsonArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return new JsonArray();
        }
        JsonElement elem = obj.get(key);
        return elem.isJsonArray() ? elem.getAsJsonArray() : new JsonArray();
    }

    /**
     * 构造 JSON 请求体
     */
    protected static RequestBody createJsonBody(String json) {
        return RequestBody.create(json, JSON);
    }

    /**
     * 按 recency 计算 ISO 8601 起始日期（Exa 等需要绝对日期的供应商）
     *
     * @return 如 "2026-07-17"（week 时减 7 天），无 recency 时返回 null
     */
    protected static String computeStartDate(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        LocalDate today = LocalDate.now();
        return switch (recency) {
            case RECENCY_DAY -> today.minusDays(1).toString();
            case RECENCY_WEEK -> today.minusDays(7).toString();
            case RECENCY_MONTH -> today.minusDays(30).toString();
            case RECENCY_YEAR -> today.minusDays(365).toString();
            default -> null;
        };
    }
}
