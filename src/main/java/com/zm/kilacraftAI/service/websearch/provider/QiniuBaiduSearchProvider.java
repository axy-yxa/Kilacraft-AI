package com.zm.kilacraftAI.service.websearch.provider;

import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.WebConfigManager;
import com.zm.kilacraftAI.service.websearch.AbstractSearchProvider;
import com.zm.kilacraftAI.service.websearch.SearchResult;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.ArrayList;
import java.util.List;

/**
 * 七牛云百度搜索 Provider
 *
 * <p>API: POST https://api.qnaigc.com/v1/search/web
 * 鉴权: Authorization: Bearer <API Key>
 * 免费额度: 300万 Token 新用户</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class QiniuBaiduSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://api.qnaigc.com/v1/search/web";

    public QiniuBaiduSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "qiniu_baidu";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isQiniuBaiduConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getQiniuBaiduApiKey() : "";

        JsonObject json = new JsonObject();
        json.addProperty("query", query);
        json.addProperty("max_results", count);
        String tf = mapRecency(recency);
        if (tf != null) json.addProperty("time_filter", tf);

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // 七牛云返回: { "data": { "results": [ { "title": "", "url": "", "content": "" } ] } }
        JsonObject data = responseJson.has("data") && responseJson.get("data").isJsonObject() ? responseJson.getAsJsonObject("data") : responseJson;
        var items = getJsonArray(data, "results");
        int limit = Math.min(items.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "url");
            String snippet = getJsonString(item, "content");
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → 七牛云 time_filter（不支持 day）
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return switch (recency) {
            case "week" -> "week";
            case "month" -> "month";
            case "year" -> "year";
            default -> null;
        };
    }
}
