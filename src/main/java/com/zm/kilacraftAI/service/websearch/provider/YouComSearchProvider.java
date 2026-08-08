package com.zm.kilacraftAI.service.websearch.provider;

import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.WebConfigManager;
import com.zm.kilacraftAI.service.websearch.AbstractSearchProvider;
import com.zm.kilacraftAI.service.websearch.SearchResult;
import okhttp3.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * You.com 搜索 Provider
 *
 * <p>API: GET https://ydc-index.io/v1/search
 * 鉴权: X-API-Key header
 * 免费额度: $100 免费额度</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class YouComSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://ydc-index.io/v1/search";

    public YouComSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "you_com";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isYouComConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getYouComApiKey() : "";

        String url = API_URL + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=" + count;
        String fr = mapRecency(recency);
        if (fr != null) url += "&freshness=" + fr;

        return new Request.Builder().url(url).header("X-API-Key", apiKey).header("Accept", "application/json").get().build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // You.com 返回格式: { "hits": [ { "title": "", "url": "", "description": "" } ] }
        var hitsArray = getJsonArray(responseJson, "hits");
        if (hitsArray.isEmpty()) {
            hitsArray = getJsonArray(responseJson, "results");
        }
        int limit = Math.min(hitsArray.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = hitsArray.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "url");
            String snippet = getJsonString(item, "description");
            if (snippet.isEmpty()) {
                snippet = getJsonString(item, "content");
            }
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → You.com freshness
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
