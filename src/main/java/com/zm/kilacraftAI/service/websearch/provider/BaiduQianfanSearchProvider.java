package com.zm.kilacraftAI.service.websearch.provider;

import com.google.gson.JsonArray;
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
 * 百度千帆 AI Search Provider（纯 web_search 端点，返回结构化引用列表）
 *
 * <p>API: POST https://qianfan.baidubce.com/v2/ai_search/web_search
 * 鉴权: Authorization: Bearer <API Key>
 * 免费额度: 100次/天</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class BaiduQianfanSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://qianfan.baidubce.com/v2/ai_search/web_search";

    public BaiduQianfanSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "baidu_qianfan";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isBaiduQianfanConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getBaiduQianfanApiKey() : "";

        JsonObject body = new JsonObject();
        JsonObject msg = new JsonObject();
        msg.addProperty("content", query);
        msg.addProperty("role", "user");
        JsonArray messages = new JsonArray();
        messages.add(msg);
        body.add("messages", messages);
        body.addProperty("search_source", "baidu_search_v2");
        String rf = mapRecency(recency);
        if (rf != null) body.addProperty("search_recency_filter", rf);

        RequestBody requestBody = createJsonBody(body.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(requestBody).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // web_search 返回: { "data": { "results": [ { "title": "", "url": "", "content": "" } ] } }
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
     * recency → 百度千帆 search_recency_filter（不支持 day）
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
