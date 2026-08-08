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
 * 火山引擎豆包 独立联网搜索 Provider
 *
 * <p>API: POST https://open.feedcoopapi.com/search_api/web_search
 * 鉴权: 独立的联网搜索 API Key（非 Ark API Key，需在
 * https://console.volcengine.com/search-infinity/api-key 获取）
 * 免费额度: 500次/月</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class VolcengineDoubaoSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://open.feedcoopapi.com/search_api/web_search";

    public VolcengineDoubaoSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "volcengine_doubao";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isVolcengineDoubaoConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getVolcengineDoubaoApiKey() : "";

        JsonObject json = new JsonObject();
        json.addProperty("query", query);
        json.addProperty("count", count);
        json.addProperty("searchType", "web");
        String tr = mapRecency(recency);
        if (tr != null) json.addProperty("timeRange", tr);

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // 返回格式: { "code": 200, "data": { "web": [ { "title": "", "url": "", "content": "" } ] } }
        JsonObject data = responseJson.has("data") && responseJson.get("data").isJsonObject() ? responseJson.getAsJsonObject("data") : responseJson;
        var items = getJsonArray(data, "web");
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
     * recency → 豆包 timeRange
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return switch (recency) {
            case "day" -> "OneDay";
            case "week" -> "OneWeek";
            case "month" -> "OneMonth";
            case "year" -> "OneYear";
            default -> null;
        };
    }
}
