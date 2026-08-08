package com.zm.kilacraftAI.skills.webfetch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.config.WebConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.ThinkingModelCapable;
import com.zm.kilacraftAI.skills.framework.*;
import okhttp3.*;
import okio.BufferedSource;
import org.bukkit.entity.Player;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 网页抓取技能：抓取指定 URL 的网页内容，提取正文后供 LLM 总结。
 *
 * <p>零配置——纯本地 OkHttp+Jsoup 实现，无需任何 API Key。内建 SSRF 防护：
 * 协议白名单（强制 HTTPS）+ 自定义 {@link Dns} 把 IP 内网检查内嵌进 DNS 解析，
 * 消除「检查 IP 与实际连接」两次独立 DNS 之间的 TOCTOU（DNS rebinding）窗口。
 * 响应体按字节硬上限读取，防止超大页面导致 OOM。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class WebFetchSkill implements Skill {

    private static final String SKILL_NAME = "web_fetch";
    private static final String LOG_PREFIX = "网页抓取";

    private final SkillConfigManager configManager;

    /**
     * SSRF 防护 DNS：把内网/回环/链路本地地址拒绝内嵌进 OkHttp 的 DNS 解析环节。
     *
     * <p>关键不变量——OkHttp 实际连接的 IP 就是此处校验通过的 IP，不存在「校验一次、
     * 连接另一次」的两次独立 DNS 解析，从根上消除 DNS rebinding（TTL=0 攻击者中途
     * 把域名解析切到内网）的 TOCTOU 窗口。校验失败的地址以 {@link UnknownHostException}
     * 抛出，OkHttp 不会尝试连接。</p>
     */
    private static final Dns SSRF_GUARD_DNS = hostname -> {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                throw new UnknownHostException(I18nService.tr("SSRF 防护：拒绝访问内网地址 {}", addr.getHostAddress()));
            }
        }
        return addresses;
    };

    public WebFetchSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return config != null ? config.getDescription() : "";
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return config != null ? config.getHints() : List.of();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.WEB_FETCH.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        WebConfigManager cm = KilacraftAI.getInstance().getWebConfigManager();
        return cm != null && cm.isFetchEnabled();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // 权限校验：player 为 null 视为无权限（正常调用路径必有在线 player）
        Player player = context.getPlayer();
        if (player == null || !PluginPermissionEnum.WEB_FETCH.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.WEB_FETCH.getNode())));
        }

        String action = context.getAction();
        if (!"fetch".equals(action)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
        }

        WebConfigManager cm = KilacraftAI.getInstance().getWebConfigManager();
        if (cm == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("Web 抓取配置未初始化")));
        }

        String url = SkillEntityHelper.getString(context, "url");
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请提供你想查看的网页链接")));
        }

        String question = SkillEntityHelper.getString(context, "question");
        if (question == null || question.isBlank()) {
            question = "";
        }

        final String finalUrl = url.trim();
        final String finalQuestion = question;

        // SSRF 防护与抓取均走异步——checkSsrf 中的 DNS 解析是阻塞 IO，不能在 execute
        // 同步路径执行（execute 由 SkillManager 同步调用，调用栈可能在主线程/区域线程）。
        // 异步抓取，skill 自管超时（框架不施加 execute 超时）
        int timeout = cm.getFetchTimeoutSeconds();
        final boolean ssrfEnabled = cm.isFetchSsrfProtection();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doFetch(finalUrl, cm, timeout, ssrfEnabled);
            } catch (Exception e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("网页抓取异常: {}", e.getMessage()), e);
                return new FetchResult(null, I18nService.tr("网页抓取失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool()).orTimeout(timeout, TimeUnit.SECONDS).thenApply(result -> {
            if (result.title == null) {
                return SkillResult.failure(result.content);
            }
            return buildResult(result.title, result.content, finalUrl, finalQuestion);
        }).exceptionally(e -> SkillResult.failure(I18nService.tr("网页抓取超时: {}", finalUrl), e));
    }

    private SkillResult buildResult(String title, String content, String url, String question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("title", title);
        data.put("content", content);
        if (!question.isEmpty()) {
            data.put("question", question);
        }

        StringBuilder msg = new StringBuilder();
        msg.append(I18nService.tr("已抓取网页: {}", title.isEmpty() ? url : title));
        if (!question.isEmpty()) {
            msg.append("\n").append(I18nService.tr("问题: {}", question));
        }
        if (content != null && !content.isEmpty()) {
            msg.append("\n---\n").append(content);
        }
        return SkillResult.success(msg.toString(), data);
    }

    private FetchResult doFetch(String url, WebConfigManager cm, int timeoutSeconds, boolean ssrfEnabled) throws Exception {
        OkHttpClient client = getHttpClient();
        if (client == null) {
            return new FetchResult(null, I18nService.tr("HTTP 客户端不可用"));
        }
        // derived client：注入超时 + 禁用自动重定向（手动逐跳处理，每跳重做 SSRF 校验）；
        // 开启 SSRF 时把 IP 检查内嵌进 DNS 解析，消除 DNS rebinding 的 TOCTOU 窗口。
        OkHttpClient.Builder builder = client.newBuilder().readTimeout(timeoutSeconds, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false);
        if (ssrfEnabled) {
            builder.dns(SSRF_GUARD_DNS);
        }
        OkHttpClient timedClient = builder.build();

        long maxBodyBytes = (long) cm.getFetchMaxBodySizeMb() * 1024 * 1024;
        String currentUrl = normalizeScheme(url, ssrfEnabled);
        int maxRedirects = 3;
        for (int redirect = 0; redirect <= maxRedirects; redirect++) {
            // 每跳协议白名单校验（DNS 层 IP 检查由 OkHttp 在连接时统一做，无需在此重复）
            String schemeErr = checkScheme(currentUrl, ssrfEnabled);
            if (schemeErr != null) {
                return new FetchResult(null, schemeErr);
            }

            Request request = new Request.Builder().url(currentUrl).header("User-Agent", "Kilacraft-AI/" + KilacraftAI.getInstance().getDescription().getVersion()).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8").get().build();

            try (Response response = timedClient.newCall(request).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        return new FetchResult(null, I18nService.tr("网页抓取失败：重定向缺少 Location 头"));
                    }
                    currentUrl = normalizeScheme(resolveRedirectUrl(currentUrl, location), ssrfEnabled);
                    if (redirect >= maxRedirects) {
                        return new FetchResult(null, I18nService.tr("网页抓取失败：重定向次数过多"));
                    }
                    continue;
                }

                if (!response.isSuccessful()) {
                    return new FetchResult(null, I18nService.tr("网页抓取失败，HTTP {}", response.code()));
                }

                String body = readBodyWithLimit(response, maxBodyBytes);

                Document doc = Jsoup.parse(body);
                doc.select("script, style, nav, footer, header, aside, noscript, iframe").remove();
                doc.title();
                String title = doc.title().trim();
                String text = Jsoup.clean(doc.body().html(), Safelist.none());
                if (text.isBlank()) {
                    text = doc.body().text();
                }

                int maxChars = cm.getFetchMaxTextChars();
                if (text.length() > maxChars) {
                    PluginLoggerUtil.info(LOG_PREFIX, I18nService.tr("网页内容过大（{} 字符），已截取前 {} 字符", text.length(), maxChars));
                    text = text.substring(0, maxChars);
                }

                return new FetchResult(title, text);
            }
        }
        return new FetchResult(null, I18nService.tr("网页抓取失败：重定向次数过多"));
    }

    /**
     * 字节级读取响应体，峰值内存受 maxBytes+1 严格限制，防止超大响应导致 OOM。
     *
     * <p>读取 maxBytes+1 字节用于判断是否超限：恰好等于 maxBytes+1 说明实际响应更大，
     * 截断到 maxBytes 后仍按现有「截断后解析」语义处理（不返回失败）。</p>
     */
    private String readBodyWithLimit(Response response, long maxBytes) throws Exception {
        ResponseBody body = response.body();
        if (body == null) {
            return "";
        }
        Charset charset = body.contentType() != null && body.contentType().charset() != null ? body.contentType().charset() : StandardCharsets.UTF_8;
        try (BufferedSource source = body.source()) {
            byte[] data = source.readByteArray(maxBytes + 1);
            if (data.length > maxBytes) {
                PluginLoggerUtil.info(LOG_PREFIX, I18nService.tr("响应体超过 {} 字节上限，已截断", maxBytes));
                data = Arrays.copyOf(data, (int) maxBytes);
            }
            return new String(data, charset);
        }
    }

    /**
     * 解析重定向 Location（处理相对路径）
     */
    private String resolveRedirectUrl(String baseUrl, String location) throws URISyntaxException {
        URI base = new URI(baseUrl);
        URI resolved = base.resolve(location);
        return resolved.toString();
    }

    /**
     * 强制 HTTPS：明文 HTTP 抓取存在降级/中间人注入风险，统一升级到 HTTPS。
     * SSRF 关闭时不升级（兼容内网 http 调试场景，由服主自担风险）。
     */
    private static String normalizeScheme(String url, boolean ssrfEnabled) {
        if (!ssrfEnabled) {
            return url;
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)) {
            return "https://" + url.substring(7);
        }
        return url;
    }

    /**
     * 协议白名单校验（无 IO，可在任意路径调用）。内网 IP 检查由 {@link #SSRF_GUARD_DNS}
     * 在 OkHttp 连接时统一完成，无需在此重复 DNS 解析——避免「校验一次、连接另一次」
     * 的两次独立 DNS 解析导致的 DNS rebinding TOCTOU。
     *
     * @return null 表示协议通过，非 null 表示拒绝原因
     */
    private static String checkScheme(String url, boolean ssrfEnabled) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return I18nService.tr("SSRF 防护：URL 缺少协议 {}", url);
            }
            if (ssrfEnabled) {
                // 强制 HTTPS 后只允许 https
                if (!scheme.equalsIgnoreCase("https")) {
                    return I18nService.tr("SSRF 防护：拒绝非 HTTPS 协议 {}", scheme);
                }
            } else {
                if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                    return I18nService.tr("SSRF 防护：拒绝非 HTTP(S) 协议 {}", scheme);
                }
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return I18nService.tr("SSRF 防护：URL 缺少主机名 {}", url);
            }
            return null;
        } catch (Exception e) {
            return I18nService.tr("SSRF 防护：拒绝访问 {}", url);
        }
    }

    private OkHttpClient getHttpClient() {
        if (KilacraftAI.getInstance().getLlmManager() != null && KilacraftAI.getInstance().getLlmManager().getCurrentProvider() instanceof ThinkingModelCapable capable) {
            return capable.getSharedHttpClient();
        }
        return null;
    }

    private record FetchResult(String title, String content) {
    }
}
