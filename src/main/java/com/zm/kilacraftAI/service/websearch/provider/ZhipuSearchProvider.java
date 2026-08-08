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
 * 智谱 AI Web Search Provider
 *
 * <p>API: POST https://open.bigmodel.cn/api/paas/v4/web_search
 * 鉴权: Bearer Token
 * 支持 search_engine 切换: search_std / search_pro / search_pro_sogou / search_pro_quark</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class ZhipuSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/web_search";

    public ZhipuSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "zhipu";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isZhipuConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getZhipuApiKey() : "";
        String searchEngine = cm != null ? cm.getZhipuSearchEngine() : "search_std";

        JsonObject json = new JsonObject();
        json.addProperty("request_id", "kila_" + System.currentTimeMillis());
        json.addProperty("search_query", query);
        json.addProperty("search_engine", searchEngine);
        json.addProperty("search_intent", false);
        json.addProperty("count", count);
        json.addProperty("content_size", "high");
        // recency → search_recency_filter 映射
        String filter = mapRecency(recency);
        if (filter != null) {
            json.addProperty("search_recency_filter", filter);
        }

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        var searchResult = getJsonArray(responseJson, "search_result");
        int limit = Math.min(searchResult.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = searchResult.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "link");
            String snippet = getJsonString(item, "content");
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    /**
     * recency → Zhipu search_recency_filter
     */
    private static String mapRecency(String recency) {
        if (recency == null || recency.isEmpty()) return null;
        return switch (recency) {
            case "day" -> "oneDay";
            case "week" -> "oneWeek";
            case "month" -> "oneMonth";
            case "year" -> "oneYear";
            default -> null;
        };
    }
}
