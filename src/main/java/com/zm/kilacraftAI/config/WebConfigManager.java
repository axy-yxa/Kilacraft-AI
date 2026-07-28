package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Web 搜索与抓取配置管理器
 *
 * <p>管理独立的 web.yml 配置文件，涵盖多供应商搜索 API Key 与网页抓取参数。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public class WebConfigManager {

    private static final String CONFIG_FILE = "web.yml";

    private final KilacraftAI plugin;
    private File configFile;

    @Getter
    private volatile boolean searchEnabled;
    @Getter
    private volatile String searchProvider;
    @Getter
    private volatile int searchResultCount;
    @Getter
    private volatile int searchMaxResultCount;
    @Getter
    private volatile int searchMaxSnippetChars;

    @Getter
    private volatile String zhipuApiKey;
    @Getter
    private volatile String zhipuSearchEngine;

    @Getter
    private volatile String baiduQianfanApiKey;

    @Getter
    private volatile String volcengineDoubaoApiKey;

    @Getter
    private volatile String qiniuBaiduApiKey;

    @Getter
    private volatile String alibabaIqsApiKey;

    @Getter
    private volatile String tavilyApiKey;

    @Getter
    private volatile String braveApiKey;

    @Getter
    private volatile String exaApiKey;

    @Getter
    private volatile String youComApiKey;

    @Getter
    private volatile boolean fetchEnabled;
    @Getter
    private volatile int fetchMaxBodySizeMb;
    @Getter
    private volatile int fetchMaxTextChars;
    @Getter
    private volatile int fetchTimeoutSeconds;
    @Getter
    private volatile boolean fetchSsrfProtection;

    public WebConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.searchEnabled = yaml.getBoolean("web.search.enabled", true);
        this.searchProvider = yaml.getString("web.search.provider", "auto");
        this.searchResultCount = yaml.getInt("web.search.result_count", 5);
        this.searchMaxResultCount = yaml.getInt("web.search.max_result_count", 10);
        this.searchMaxSnippetChars = yaml.getInt("web.search.max_snippet_chars", 800);

        this.zhipuApiKey = yaml.getString("web.search.zhipu.api_key", "");
        this.zhipuSearchEngine = yaml.getString("web.search.zhipu.search_engine", "search_std");

        this.baiduQianfanApiKey = yaml.getString("web.search.baidu_qianfan.api_key", "");

        this.volcengineDoubaoApiKey = yaml.getString("web.search.volcengine_doubao.api_key", "");

        this.qiniuBaiduApiKey = yaml.getString("web.search.qiniu_baidu.api_key", "");

        this.alibabaIqsApiKey = yaml.getString("web.search.alibaba_iqs.api_key", "");

        this.tavilyApiKey = yaml.getString("web.search.tavily.api_key", "");
        this.braveApiKey = yaml.getString("web.search.brave.api_key", "");
        this.exaApiKey = yaml.getString("web.search.exa.api_key", "");
        this.youComApiKey = yaml.getString("web.search.you_com.api_key", "");

        this.fetchEnabled = yaml.getBoolean("web.fetch.enabled", true);
        this.fetchMaxBodySizeMb = yaml.getInt("web.fetch.max_body_size_mb", 2);
        this.fetchMaxTextChars = yaml.getInt("web.fetch.max_text_chars", 8000);
        this.fetchTimeoutSeconds = yaml.getInt("web.fetch.timeout_seconds", 15);
        this.fetchSsrfProtection = yaml.getBoolean("web.fetch.ssrf_protection", true);

        PluginLoggerUtil.info("网页搜索", I18nService.tr("Web 配置已加载，供应商: {}", searchProvider));
    }

    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    public boolean isZhipuConfigured() {
        return zhipuApiKey != null && !zhipuApiKey.isEmpty();
    }

    public boolean isBaiduQianfanConfigured() {
        return baiduQianfanApiKey != null && !baiduQianfanApiKey.isEmpty();
    }

    public boolean isVolcengineDoubaoConfigured() {
        return volcengineDoubaoApiKey != null && !volcengineDoubaoApiKey.isEmpty();
    }

    public boolean isQiniuBaiduConfigured() {
        return qiniuBaiduApiKey != null && !qiniuBaiduApiKey.isEmpty();
    }

    public boolean isAlibabaIqsConfigured() {
        return alibabaIqsApiKey != null && !alibabaIqsApiKey.isEmpty();
    }

    public boolean isTavilyConfigured() {
        return tavilyApiKey != null && !tavilyApiKey.isEmpty();
    }

    public boolean isBraveConfigured() {
        return braveApiKey != null && !braveApiKey.isEmpty();
    }

    public boolean isExaConfigured() {
        return exaApiKey != null && !exaApiKey.isEmpty();
    }

    public boolean isYouComConfigured() {
        return youComApiKey != null && !youComApiKey.isEmpty();
    }
}
