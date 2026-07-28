package com.zm.kilacraftAI.skills.websearch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.config.WebConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.websearch.SearchProvider;
import com.zm.kilacraftAI.service.websearch.SearchResult;
import com.zm.kilacraftAI.service.websearch.provider.*;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 联网搜索技能：从互联网获取实时信息。
 *
 * <p>支持多供应商可插拔：国内（智谱/百度千帆/...）和国际（Tavily/Brave/...），
 * 按 i18n 语言与配置自动选择首个已配置的供应商。通过 {@link DynamicContextProvider}
 * 在 Phase 2 提示词中注入多搜索编排指南，引导 LLM 将复杂问题分解为多个搜索步骤。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class WebSearchSkill implements Skill, DynamicContextProvider {

    private static final String PACKAGE = "websearch";
    private static final String SKILL_NAME = "WebSearchSkill";

    /**
     * 搜索请求超时（秒），skill 自管，框架不施加 execute 超时
     */
    private static final long SEARCH_TIMEOUT_SECONDS = 15;

    private final List<SearchProvider> providers;

    public WebSearchSkill() {
        SkillConfigManager cm = SkillConfigManager.getInstance();
        if (cm != null && cm.getSkillConfig(PACKAGE, SKILL_NAME) == null) {
            cm.saveDefaultSkillConfig(PACKAGE, SKILL_NAME);
            cm.loadSingleSkillConfig(PACKAGE, SKILL_NAME);
        }

        KilacraftAI plugin = KilacraftAI.getInstance();
        this.providers = new ArrayList<>();
        // 国内供应商（按优先级顺序）
        providers.add(new ZhipuSearchProvider(plugin));
        providers.add(new BaiduQianfanSearchProvider(plugin));
        providers.add(new QiniuBaiduSearchProvider(plugin));
        providers.add(new VolcengineDoubaoSearchProvider(plugin));
        providers.add(new AlibabaIQSSearchProvider(plugin));
        // 国际供应商（按优先级顺序）
        providers.add(new TavilySearchProvider(plugin));
        providers.add(new BraveSearchProvider(plugin));
        providers.add(new ExaSearchProvider(plugin));
        providers.add(new YouComSearchProvider(plugin));
    }

    private SkillConfig getConfig() {
        SkillConfigManager cm = SkillConfigManager.getInstance();
        return cm != null ? cm.getSkillConfig(PACKAGE, SKILL_NAME) : null;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return config != null ? config.getDescription() : "";
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return config != null ? config.getActionDescriptions() : new HashMap<>();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return config != null ? config.getHints() : List.of();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.WEB_SEARCH.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        WebConfigManager cm = KilacraftAI.getInstance().getWebConfigManager();
        return cm != null && cm.isSearchEnabled();
    }

    @Override
    public String getDynamicContext(Player player) {
        return I18nService.tr("""
                【web_search 多搜索编排指南】
                当用户问题包含多个独立子问题时，你可以创建多个 web_search 步骤。
                适合拆分场景：
                - 用户明确问了多个独立问题（如"A怎么样？B呢？C呢？"）
                - 需要对比多个事物（如"Java版和Bedrock版有什么区别"）
                - 搜索范围广需多角度切入
                拆分原则：每个搜索聚焦单一主题，最多拆成 5 个。
                注意：多个搜索步骤会按顺序依次执行，请合理控制数量。
                """);
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // 权限校验：player 为 null 视为无权限（正常调用路径必有在线 player）
        Player player = context.getPlayer();
        if (player == null || !PluginPermissionEnum.WEB_SEARCH.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.WEB_SEARCH.getNode())));
        }

        String action = context.getAction();
        if (!"search".equals(action)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
        }

        WebConfigManager cm = KilacraftAI.getInstance().getWebConfigManager();
        if (cm == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("Web 搜索配置未初始化")));
        }

        String query = context.getEntity("query");
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我你想搜索什么关键词")));
        }

        // 选择供应商
        SearchProvider provider = selectProvider(cm);
        if (provider == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("联网搜索功能未配置 API Key，请联系服主在 web.yml 中配置搜索供应商的 API Key")));
        }

        String recency = context.getEntity("recency");
        int count = resolveCount(context, cm);

        // 异步执行搜索，skill 自管超时（框架不施加 execute 超时）
        return provider.search(query, count, recency).orTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS).thenApply(results -> {
            if (results.isEmpty()) {
                return SkillResult.success(I18nService.tr("未找到相关搜索结果"), null);
            }
            // 截断过长的 snippet，避免单条结果占满 LLM 上下文
            int maxSnippetChars = cm.getSearchMaxSnippetChars();
            List<SearchResult> truncated = new ArrayList<>();
            for (SearchResult r : results) {
                truncated.add(r.withTruncatedSnippet(maxSnippetChars));
            }
            SummaryAndData sad = buildSummary(query, provider.getProviderName(), truncated);
            return SkillResult.success(sad.summary, sad.data);
        }).exceptionally(e -> {
            Throwable cause = e;
            // 解包 CompletableFuture 的 CompletionException 包装层
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            // 沿 cause 链查找超时（orTimeout 抛 TimeoutException 可能被多层包装）
            Throwable t = cause;
            while (t != null) {
                if (t instanceof java.util.concurrent.TimeoutException) {
                    return SkillResult.failure(I18nService.tr("搜索请求超时"));
                }
                t = t.getCause();
            }
            if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
                return SkillResult.failure(cause.getMessage());
            }
            PluginLoggerUtil.warn("网页搜索", I18nService.tr("搜索请求异常"), cause);
            return SkillResult.failure(I18nService.tr("搜索请求失败"));
        });
    }

    /**
     * 按配置选择供应商：auto 模式按 i18n 语言遍历国内/国际列表，手动指定则直接选
     */
    private SearchProvider selectProvider(WebConfigManager cm) {
        String config = cm.getSearchProvider();
        boolean isChinese = I18nService.isZh();

        if ("auto".equalsIgnoreCase(config)) {
            // auto 模式：按语言遍历对应列表，返回首个已配置的
            for (SearchProvider p : providers) {
                if (isDomestic(p.getProviderName()) == isChinese && p.isConfigured()) {
                    return p;
                }
            }
            // 回退：语言列表全未配则尝试另一组
            for (SearchProvider p : providers) {
                if (p.isConfigured()) {
                    return p;
                }
            }
            return null;
        }

        // 手动指定
        for (SearchProvider p : providers) {
            if (p.getProviderName().equalsIgnoreCase(config) && p.isConfigured()) {
                return p;
            }
        }
        return null;
    }

    /**
     * 判断供应商是否为国内供应商
     */
    private static boolean isDomestic(String providerName) {
        return providerName.equals("zhipu") || providerName.equals("baidu_qianfan") || providerName.equals("qiniu_baidu") || providerName.equals("volcengine_doubao") || providerName.equals("alibaba_iqs");
    }

    /**
     * 解析结果条数：LLM 可通过 count entity 动态指定，未指定用默认值，两者均不超过硬上限
     */
    private static int resolveCount(SkillContext context, WebConfigManager cm) {
        String countStr = context.getEntity("count");
        int count;
        if (countStr != null && !countStr.isBlank()) {
            try {
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                count = cm.getSearchResultCount();
            }
        } else {
            count = cm.getSearchResultCount();
        }
        return Math.max(1, Math.min(count, cm.getSearchMaxResultCount()));
    }

    /**
     * 构造搜索结果摘要与数据。
     * <p>摘要（message）同时包含标题、URL 和内容 snippet——LLM 二次分析只看 message 不看 data，
     * 若缺少 snippet 则 LLM 只能根据标题瞎猜。</p>
     */
    private SummaryAndData buildSummary(String query, String provider, List<SearchResult> results) {
        // data 供 TaskExecutor 占位符解析（如 {step_0.results[0].title}），必须转 Map 否则 resolveFieldPath 返回 null
        List<Map<String, Object>> resultMaps = new ArrayList<>();
        for (SearchResult r : results) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("title", r.title());
            rm.put("url", r.url());
            rm.put("snippet", r.snippet());
            resultMaps.add(rm);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("query", query);
        data.put("results", resultMaps);
        data.put("total_count", results.size());

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("搜索到 {} 条结果：", results.size()));
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("\n").append(i + 1).append(". ").append(r.title());
            if (!r.url().isEmpty()) {
                sb.append(" (").append(r.url()).append(")");
            }
            if (r.snippet() != null && !r.snippet().isEmpty()) {
                sb.append("\n   ").append(r.snippet());
            }
        }
        return new SummaryAndData(sb.toString(), data);
    }

    private record SummaryAndData(String summary, Map<String, Object> data) {
    }
}
