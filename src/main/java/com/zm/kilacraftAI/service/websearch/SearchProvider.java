package com.zm.kilacraftAI.service.websearch;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 搜索供应商统一接口
 *
 * <p>所有搜索引擎 API（智谱/Tavily/百度千帆/Brave 等）实现此接口</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public interface SearchProvider {

    /**
     * 执行联网搜索
     *
     * @param query   搜索关键词
     * @param count   期望返回结果条数
     * @param recency 时间范围——day / week / month / year，null 表示不限
     * @return 搜索结果列表（异步）
     */
    CompletableFuture<List<SearchResult>> search(String query, int count, String recency);

    /**
     * 该供应商是否已正确配置（API Key 非空且有效）
     *
     * @return true 表示可立即调用 search
     */
    boolean isConfigured();

    /**
     * 供应商标识名（与 web.yml 中 provider 配置项匹配）
     *
     * @return 如 "zhipu" / "tavily"
     */
    String getProviderName();
}
