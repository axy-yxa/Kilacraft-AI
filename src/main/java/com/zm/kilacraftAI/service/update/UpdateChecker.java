package com.zm.kilacraftAI.service.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 版本更新检测器
 *
 * @author Zm_Mmm
 * @since 2026-06-12
 */
public class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/axy-yxa/Kilacraft-AI/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    /** 响应体最大允许字节数（256 KB），防止异常响应导致 OOM */
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;

    /**
     * 彩色框线的固定宽度（等宽字符数）
     */
    private static final int BOX_WIDTH = 50;
    private static final String BOX_LINE = "═".repeat(BOX_WIDTH);

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
     * 同步请求 GitHub Releases API 获取最新版本信息
     *
     * @return 最新版本信息，请求失败时返回 null
     */
    private ReleaseInfo fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Kilacraft-AI/" + currentVersion);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
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

            JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String tagName = json.get("tag_name").getAsString();
            String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : tagName;
            String htmlUrl = json.has("html_url") && !json.get("html_url").isJsonNull() ? json.get("html_url").getAsString() : "";
            String publishedAt = json.has("published_at") && !json.get("published_at").isJsonNull() ? json.get("published_at").getAsString() : "";

            // 截取日期部分
            if (publishedAt.length() > 10) {
                publishedAt = publishedAt.substring(0, 10);
            }

            return new ReleaseInfo(tagName, name, htmlUrl, publishedAt);
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
     * 比较两个语义版本号，判断 latest 是否比 current 更新
     *
     * <p>支持带 {@code v} 前缀的版本号（如 {@code v2.1.0} → {@code 2.1.0}）。</p>
     *
     * @param current 当前版本
     * @param latest  远程最新版本
     * @return true 表示有新版本可用
     */
    static boolean isNewerVersion(String current, String latest) {
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

        Bukkit.getConsoleSender().sendMessage(yellow + "╔" + BOX_LINE + "╗");
        Bukkit.getConsoleSender().sendMessage(yellow + "║" + reset + "  " + yellow + "✦ " + gold + "新版本可用" + white + " / " + yellow + "New Version Available" + yellow + "  ✦" + reset);
        Bukkit.getConsoleSender().sendMessage(yellow + "╠" + BOX_LINE + "╣");
        Bukkit.getConsoleSender().sendMessage(yellow + "║" + reset + "  " + white + bold + release.name + reset);
        Bukkit.getConsoleSender().sendMessage(yellow + "║" + reset + "  " + gray + "当前 / Current: " + reset + white + "v" + currentVersion + reset);
        if (!release.publishedAt.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(yellow + "║" + reset + "  " + gray + "发布 / Released: " + reset + white + release.publishedAt + reset);
        }
        if (!release.htmlUrl.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(yellow + "║" + reset + "  " + gray + "下载 / Download: " + reset + aqua + underline + release.htmlUrl + reset);
        }
        Bukkit.getConsoleSender().sendMessage(yellow + "╚" + BOX_LINE + "╝");
    }

    /**
     * GitHub Release 信息
     */
    private record ReleaseInfo(String tagName, String name, String htmlUrl, String publishedAt) {
    }
}
