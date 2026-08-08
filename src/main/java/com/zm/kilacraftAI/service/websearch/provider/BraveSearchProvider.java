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
 * Brave Search API Provider
 *
 * <p>API: GET https://api.search.brave.com/res/v1/web/search
 * 鉴权: X-Subscription-Token header
 * 免费额度: $5/月免费额度，独立索引(40B+页)</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class BraveSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://api.search.brave.com/res/v1/web/search";

    public BraveSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "brave";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isBraveConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getBraveApiKey() : "";

        String url = API_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=" + count + "&safesearch=moderate";
        String fr = mapRecency(recency);
        if (fr != null) url += "&freshness=" + fr;

        return new Request.Builder().url(url).header("X-Subscription-Token", apiKey).header("Accept", "application/json").get().build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // Brave 返回格式: { "web": { "results": [ { "title": "", "url": "", "description": "" } ] } }
        JsonObject web = responseJson.has("web") && responseJson.get("web").isJsonObject() ? responseJson.getAsJsonObject("web") : responseJson;
        var resultsArray = getJsonArray(web, "results");
        int limit = Math.min(resultsArray.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = resultsArray.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "url");
            String snippet = getJsonString(item, "description");
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → Brave freshness
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return switch (recency) {
            case "day" -> "pd";
            case "week" -> "pw";
            case "month" -> "pm";
            case "year" -> "py";
            default -> null;
        };
    }
}
