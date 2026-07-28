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
 * Exa 神经语义搜索 Provider
 *
 * <p>API: POST https://api.exa.ai/search
 * 鉴权: x-api-key header
 * 免费额度: 1000次/月，embedding 级语义理解，"Find Similar"独有功能</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class ExaSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://api.exa.ai/search";

    public ExaSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "exa";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isExaConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getExaApiKey() : "";

        JsonObject json = new JsonObject();
        json.addProperty("query", query);
        json.addProperty("numResults", count);
        json.addProperty("type", "auto");
        JsonObject contents = new JsonObject();
        contents.addProperty("text", true);
        json.add("contents", contents);
        String sd = mapRecency(recency);
        if (sd != null) json.addProperty("startPublishedDate", sd);

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("x-api-key", apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // Exa 返回格式: { "results": [ { "title": "", "url": "", "text": "" } ] }
        var resultsArray = getJsonArray(responseJson, "results");
        int limit = Math.min(resultsArray.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = resultsArray.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "url");
            String snippet = getJsonString(item, "text");
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → Exa startPublishedDate
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return computeStartDate(recency);
    }
}
