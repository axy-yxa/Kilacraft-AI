package com.zm.kilacraftAI.service.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 版本更新检测器
 *
 * @author Zm_Mmm
 * @since 2026-06-12
 */
public class UpdateChecker {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    /**
     * 响应体最大允许字节数（256 KB），防止异常响应导致 OOM
     */
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;

    /**
     * 框线左右内边距（空格数）
     */
    private static final int PADDING = 2;

    private final String currentVersion;

    public UpdateChecker(JavaPlugin plugin) {
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * 异步检查更新，完成后若有新版本则在控制台输出彩色提示
     *
     * <p>已是最新版本或请求失败时静默处理，不影响插件启动。</p>
     */
    public void checkAsync() {
        CompletableFuture.supplyAsync(this::fetchLatestRelease, FoliaCompat.getIOPool()).thenAccept(result -> {
            if (result != null && isNewerVersion(currentVersion, result.tagName)) {
                printUpdateNotification(result);
            }
        }).exceptionally(ex -> {
            // 请求失败静默处理，不打扰启动日志
            return null;
        });
    }

    /**
     * 同步请求发布源（按语言选择 Gitee/GitHub）获取最新版本信息。
     * 阻塞网络调用，调用方须在 IO 线程池中执行。请求失败时返回 null。
     * 返回最新版本信息，请求失败时返回 null。
     */
    public ReleaseInfo fetchLatestRelease() {
        // 按语言环境选择发布源：中文环境（国内服务器居多）走 Gitee，其他环境走 GitHub
        ReleaseSource source = I18nService.isZh() ? ReleaseSource.GITEE : ReleaseSource.GITHUB;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(source.apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Kilacraft-AI/" + currentVersion);
            conn.setRequestProperty("Accept", source.accept);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int totalChars = 0;
                while ((line = reader.readLine()) != null) {
                    totalChars += line.length();
                    if (totalChars > MAX_RESPONSE_CHARS) {
                        // 响应体异常（超过 256 KB），静默放弃
                        return null;
                    }
                    sb.append(line);
                }
            }

            return parseRelease(JsonParser.parseString(sb.toString()).getAsJsonObject(), source);
        } catch (Exception e) {
            // 网络异常/超时/JSON解析失败均静默处理
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析 release JSON 为 {@link ReleaseInfo}，按来源适配字段差异
     *
     * <p>Gitee API 不返回 {@code html_url} 与 {@code published_at}，需分别以拼接地址、
     * {@code created_at} 兜底；GitHub 字段完整可直接读取。</p>
     *
     * @param json   API 响应对象
     * @param source 当前发布源
     * @return 解析后的发布信息
     */
    private ReleaseInfo parseRelease(JsonObject json, ReleaseSource source) {
        String tagName = json.get("tag_name").getAsString();
        String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : tagName;

        // 下载链接：Gitee 需按 tag 拼接网页地址；GitHub 直接读取响应字段
        String htmlUrl;
        if (source.htmlUrlPrefix != null) {
            htmlUrl = source.htmlUrlPrefix + tagName;
        } else {
            htmlUrl = json.has("html_url") && !json.get("html_url").isJsonNull() ? json.get("html_url").getAsString() : "";
        }

        // 发布日期：优先 published_at（GitHub），缺失时回退 created_at（Gitee）
        String publishedAt = readDateField(json, "published_at");
        if (publishedAt.isEmpty()) {
            publishedAt = readDateField(json, "created_at");
        }

        return new ReleaseInfo(tagName, name, htmlUrl, publishedAt);
    }

    /**
     * 安全读取 ISO-8601 日期字段并截取日期部分（yyyy-MM-dd）
     *
     * @param json  API 响应对象
     * @param field 日期字段名（如 published_at / created_at）
     * @return 日期字符串；字段缺失或为空时返回空串
     */
    private static String readDateField(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            return "";
        }
        String value = json.get(field).getAsString();
        return value.length() > 10 ? value.substring(0, 10) : value;
    }

    /**
     * 比较两个语义版本号，判断 latest 是否比 current 更新
     *
     * <p>支持带 {@code v} 前缀的版本号（如 {@code v2.1.0} → {@code 2.1.0}）。</p>
     *
     * @param current 当前版本
     * @param latest  远程最新版本
     * @return true 表示有新版本可用
     */
    public static boolean isNewerVersion(String current, String latest) {
        String c = stripPrefix(current);
        String l = stripPrefix(latest);

        String[] cParts = c.split("\\.");
        String[] lParts = l.split("\\.");
        int maxLen = Math.max(cParts.length, lParts.length);

        for (int i = 0; i < maxLen; i++) {
            int cNum = i < cParts.length ? parseVersionPart(cParts[i]) : 0;
            int lNum = i < lParts.length ? parseVersionPart(lParts[i]) : 0;
            if (lNum > cNum) return true;
            if (lNum < cNum) return false;
        }
        return false;
    }

    /**
     * 去除版本号前缀 v/V
     */
    private static String stripPrefix(String version) {
        if (version.startsWith("v") || version.startsWith("V")) {
            return version.substring(1);
        }
        return version;
    }

    /**
     * 解析版本号段为整数，非数字部分返回 0
     */
    private static int parseVersionPart(String part) {
        try {
            // 仅取纯数字前缀（如 "1-SNAPSHOT" → 1）
            StringBuilder digits = new StringBuilder();
            for (char ch : part.toCharArray()) {
                if (Character.isDigit(ch)) {
                    digits.append(ch);
                } else {
                    break;
                }
            }
            return !digits.isEmpty() ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 在控制台输出彩色更新提示框
     *
     * <p>框宽按内容自适应：以最长内容行的可见宽度为准，左右各留 {@value #PADDING} 空格内边距，
     * 每行右侧补空格并闭合 {@code ║}，形成完整闭环。可见宽度按全角字符 2 列、半角 1 列计算，
     * 适配中英文混排。</p>
     */
    private void printUpdateNotification(ReleaseInfo release) {
        // 颜色常量
        String yellow = ChatColor.YELLOW.toString();
        String gold = ChatColor.GOLD.toString();
        String white = ChatColor.WHITE.toString();
        String bold = ChatColor.BOLD.toString();
        String gray = ChatColor.DARK_GRAY.toString();
        String aqua = ChatColor.AQUA.toString();
        String underline = ChatColor.UNDERLINE.toString();
        String reset = ChatColor.RESET.toString();

        // 构建各内容行（去色后用于宽度计算，带色用于渲染）
        LineBuilder title = new LineBuilder().segment("新版本可用", gold).segment(" / ", white).segment("New Version Available", yellow);
        LineBuilder name = new LineBuilder().segment(release.name, white + bold);
        LineBuilder current = new LineBuilder().segment("当前 / Current: ", gray).segment("v" + currentVersion, white);
        LineBuilder released = release.publishedAt.isEmpty() ? null : new LineBuilder().segment("发布 / Released: ", gray).segment(release.publishedAt, white);
        LineBuilder download = release.htmlUrl.isEmpty() ? null : new LineBuilder().segment("下载 / Download: ", gray).segment(release.htmlUrl, aqua + underline);

        // 框内宽 = 最长内容行可见宽度
        List<LineBuilder> rows = new ArrayList<>();
        rows.add(title);
        rows.add(name);
        rows.add(current);
        if (released != null) rows.add(released);
        if (download != null) rows.add(download);
        int inner = 0;
        for (LineBuilder lb : rows) {
            inner = Math.max(inner, lb.width());
        }

        String dashes = "═".repeat(inner + PADDING * 2);
        sendMessage(yellow + "╔" + dashes + "╗");
        sendMessage(yellow + "║" + reset + pad(title, inner) + yellow + "║");
        sendMessage(yellow + "╠" + dashes + "╣");
        sendMessage(yellow + "║" + reset + pad(name, inner) + yellow + "║");
        sendMessage(yellow + "║" + reset + pad(current, inner) + yellow + "║");
        if (released != null) {
            sendMessage(yellow + "║" + reset + pad(released, inner) + yellow + "║");
        }
        if (download != null) {
            sendMessage(yellow + "║" + reset + pad(download, inner) + yellow + "║");
        }
        sendMessage(yellow + "╚" + dashes + "╝");
    }

    /**
     * 向控制台发送一条消息（集中调用，便于统一调整输出方式）
     */
    private static void sendMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * 将一行内容居左放置、右侧补空格至框内宽，左右各留 {@value #PADDING} 内边距
     *
     * @param lb    内容行
     * @param inner 框内宽（最长内容行可见宽度）
     * @return 已补齐的带色行内容（不含左右竖线）
     */
    private static String pad(LineBuilder lb, int inner) {
        int rightPad = inner - lb.width() + PADDING;
        return " ".repeat(PADDING) + lb.colored() + " ".repeat(rightPad);
    }

    /**
     * 计算字符串在等宽终端的可见显示宽度
     *
     * <p>全角字符（中日韩表意、假名、全角符号等）计 2 列，半角计 1 列。
     * 用于控制台制表符框线的右侧对齐。</p>
     *
     * @param s 文本
     * @return 可见列宽
     */
    private static int displayWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            width += isFullWidth(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    /**
     * 判断码点是否为全角字符（占用 2 个等宽列）
     *
     * <p>覆盖主要的中日韩表意文字、假名、谚文及全角符号区间。</p>
     *
     * @param cp Unicode 码点
     * @return true 表示该字符为全角
     */
    private static boolean isFullWidth(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)   // 谚文 Jamo
                || (cp >= 0x2E80 && cp <= 0x303E) // CJK 部首与标点
                || (cp >= 0x3041 && cp <= 0x33FF) // 假名 / 谚文 / CJK 符号
                || (cp >= 0x3400 && cp <= 0x4DBF) // CJK 扩展 A
                || (cp >= 0x4E00 && cp <= 0x9FFF) // CJK 统一表意
                || (cp >= 0xA000 && cp <= 0xA4CF) // 彝文
                || (cp >= 0xAC00 && cp <= 0xD7A3) // 谚文音节
                || (cp >= 0xF900 && cp <= 0xFAFF) // CJK 兼容表意
                || (cp >= 0xFE30 && cp <= 0xFE4F) // CJK 兼容形式
                || (cp >= 0xFF00 && cp <= 0xFF60) // 全角 ASCII
                || (cp >= 0xFFE0 && cp <= 0xFFE6) // 全角符号
                || (cp >= 0x20000 && cp <= 0x2FFFD) // CJK 扩展 B-F
                || (cp >= 0x30000 && cp <= 0x3FFFD); // CJK 扩展 G+
    }

    /**
     * 构建带色的提示行，同时维护去色后的可见文本，用于框线宽度计算
     */
    private static final class LineBuilder {
        private final StringBuilder colored = new StringBuilder();
        private final StringBuilder plain = new StringBuilder();

        /**
         * 追加一段着色文本（空文本自动跳过）
         *
         * @param text  文本
         * @param color 颜色前缀（可为多个 ChatColor 拼接）
         * @return 当前构建器，便于链式调用
         */
        LineBuilder segment(String text, String color) {
            if (text != null && !text.isEmpty()) {
                colored.append(color).append(text).append(ChatColor.RESET);
                plain.append(text);
            }
            return this;
        }

        /**
         * @return 带颜色码的渲染文本
         */
        String colored() {
            return colored.toString();
        }

        /**
         * @return 去色后文本的可见列宽
         */
        int width() {
            return displayWidth(plain.toString());
        }
    }

    /**
     * 版本信息（来源无关）
     */
    public record ReleaseInfo(String tagName, String name, String htmlUrl, String publishedAt) {
    }

    /**
     * 发布源：按语言环境选择，兼顾国内外网络可达性
     *
     * <p>中文环境（国内服务器居多）走 Gitee；其他环境走 GitHub。</p>
     */
    private enum ReleaseSource {
        /**
         * Gitee 镜像（国内可达；API 不返回 html_url，需拼接）
         */
        GITEE("https://gitee.com/api/v5/repos/zm_mmm/kilacraft-ai/releases/latest", "application/json", "https://gitee.com/zm_mmm/kilacraft-ai/releases/tag/"),
        /**
         * GitHub（海外可达；API 字段完整）
         */
        GITHUB("https://api.github.com/repos/axy-yxa/Kilacraft-AI/releases/latest", "application/vnd.github.v3+json", null);

        /**
         * Releases API 地址
         */
        final String apiUrl;
        /**
         * Accept 请求头
         */
        final String accept;
        /**
         * Release 网页地址前缀，需追加 tag_name；为 null 表示直接读取响应中的 html_url
         */
        final String htmlUrlPrefix;

        ReleaseSource(String apiUrl, String accept, String htmlUrlPrefix) {
            this.apiUrl = apiUrl;
            this.accept = accept;
            this.htmlUrlPrefix = htmlUrlPrefix;
        }
    }
}
