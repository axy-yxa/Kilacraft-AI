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
 * 阿里云百炼 IQS（信息查询服务）搜索 Provider
 *
 * <p>API: POST https://cloud-iqs.aliyuncs.com/search/unified
 * 鉴权: Authorization: Bearer <API Key>
 * 免费额度: 1000次/15天试用</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class AlibabaIQSSearchProvider extends AbstractSearchProvider {

    private static final String API_URL = "https://cloud-iqs.aliyuncs.com/search/unified";

    public AlibabaIQSSearchProvider(KilacraftAI plugin) {
        super(plugin);
    }

    @Override
    public String getProviderName() {
        return "alibaba_iqs";
    }

    @Override
    public boolean isConfigured() {
        WebConfigManager cm = plugin.getWebConfigManager();
        return cm != null && cm.isAlibabaIqsConfigured();
    }

    @Override
    protected Request buildRequest(String query, int count, String recency) {
        WebConfigManager cm = plugin.getWebConfigManager();
        String apiKey = cm != null ? cm.getAlibabaIqsApiKey() : "";

        JsonObject json = new JsonObject();
        json.addProperty("query", query);
        json.addProperty("engineType", "GenericAdvanced");
        JsonObject advancedParams = new JsonObject();
        advancedParams.addProperty("numResults", count);
        json.add("advancedParams", advancedParams);

        RequestBody body = createJsonBody(json.toString());
        return new Request.Builder().url(API_URL).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").post(body).build();
    }

    @Override
    protected List<SearchResult> parseResults(JsonObject responseJson, int maxCount) {
        List<SearchResult> results = new ArrayList<>();
        // 返回: { "pageItems": [ { "title": "", "link": "", "summary": "", "mainText": "" } ] }
        var items = getJsonArray(responseJson, "pageItems");
        int limit = Math.min(items.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String title = getJsonString(item, "title");
            String url = getJsonString(item, "link");
            String snippet = getJsonString(item, "summary");
            if (snippet.isEmpty()) {
                snippet = getJsonString(item, "mainText");
            }
            if (title.isEmpty() && url.isEmpty()) continue;
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }
}
