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
 * Tavily AI Search Provider
 *
 * <p>API: POST https://api.tavily.com/search
 * 鉴权: Authorization: Bearer <API Key>
 * 免费额度: 1000次/月（免信用卡）</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class TavilySearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://api.tavily.com/search";

    public TavilySearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "tavily";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isTavilyConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getTavilyApiKey() : "";

        JsonObject json = new JsonObject();
        json.addProperty("query", query);
        json.addProperty("max_results", count);
        json.addProperty("search_depth", "basic");
        json.addProperty("include_answer", false);
        String tr = mapRecency(recency);
        if (tr != null) json.addProperty("time_range", tr);

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // Tavily 返回格式: { "results": [ { "title": "", "url": "", "content": "" } ] }
        var resultsArray = getJsonArray(responseJson, "results");
        int limit = Math.min(resultsArray.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = resultsArray.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "url");
            String snippet = getJsonString(item, "content");
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → Tavily time_range
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return switch (recency) {
            case "day" -> "day";
            case "week" -> "week";
            case "month" -> "month";
            case "year" -> "year";
            default -> null;
        };
    }
}
