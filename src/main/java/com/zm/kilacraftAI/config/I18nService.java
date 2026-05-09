package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.PluginLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 国际化服务
 * 以中文原文为 key 的翻译引擎，支持 {} 占位符模板参数填充（SLF4J 风格）。
 * zh 模式直接返回原文，en/ja 等模式查翻译包。
 *
 * @author Zm_Mmm
 * @since 2026-04-21
 */
public final class I18nService {

    /**
     * 插件实例
     */
    private final KilacraftAI plugin;

    /**
     * 当前语言代码（zh、en 等）
     */
    private volatile String language;

    /**
     * 是否为中文模式（中文模式下所有翻译直接返回原文）
     */
    private volatile boolean isZh;

    /**
     * 模块名翻译映射：中文原文 → 目标语言
     */
    private volatile Map<String, String> moduleMap = Collections.emptyMap();

    /**
     * 消息翻译映射：中文原文 → 目标语言
     */
    private volatile Map<String, String> messageMap = Collections.emptyMap();

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public I18nService(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载翻译包（在插件启动时调用）
     * <p>zh 模式不加载任何翻译包，en 等模式加载对应的 messages_xx.yml</p>
     */
    public void load() {
        this.language = plugin.getConfigManager().getLanguage();
        this.isZh = "zh".equals(this.language);

        if (isZh) {
            // 中文模式：清空翻译表，所有 t() 直接返回原文
            this.moduleMap = Collections.emptyMap();
            this.messageMap = Collections.emptyMap();
            PluginLogger.info("国际化", "当前语言：中文（默认语言，无需翻译包）");
            return;
        }

        // 非 zh 模式：加载对应语言的翻译包
        String fileName = "i18n/messages_" + this.language + ".yml";
        File externalFile = new File(plugin.getDataFolder(), fileName);

        // 先确保文件存在（静默创建，不输出日志，因为翻译表尚未加载）
        boolean justCreated = false;
        if (!externalFile.exists()) {
            File parentDir = externalFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try {
                plugin.saveResource(fileName, false);
                justCreated = true;
            } catch (Exception e) {
                PluginLogger.error("国际化", I18nService.tr("创建配置文件失败: {}", fileName), e);
            }
        }

        // 重新检查文件（可能刚从 JAR 解压出来）
        externalFile = new File(plugin.getDataFolder(), fileName);
        if (externalFile.exists()) {
            loadTranslationFile(externalFile);

            // 翻译表已加载，现在可以输出翻译后的日志
            if (justCreated) {
                PluginLogger.info("配置管理", "已创建默认配置文件: {}", externalFile.getName());
            }
        } else {
            PluginLogger.warn("国际化", "翻译包不存在: {}，将回退为中文原文", fileName);
            this.moduleMap = Collections.emptyMap();
            this.messageMap = Collections.emptyMap();
        }

        PluginLogger.info("国际化", "当前语言：{}", this.language);
    }

    /**
     * 热重载翻译包
     */
    public void reload() {
        load();
    }

    /**
     * 获取当前语言代码
     *
     * @return 语言代码（如 "zh"、"en"）
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 是否为中文模式
     *
     * @return true=中文模式
     */
    public boolean isChinese() {
        return isZh;
    }

    // ==================== 翻译 API ====================

    /**
     * 翻译模块名
     * <p>zh → 直接返回 module，en → 查表翻译</p>
     *
     * @param module 模块名（中文原文）
     * @return 翻译后的模块名
     */
    public String tModule(String module) {
        if (isZh || module == null) return module;
        String translated = moduleMap.get(module);
        return translated != null ? translated : module;
    }

    /**
     * 翻译消息（无参数）
     * <p>zh → 直接返回 message，en → 查表翻译</p>
     *
     * @param message 消息文本（中文原文）
     * @return 翻译后的消息
     */
    public String t(String message) {
        if (isZh || message == null) return message;
        String translated = messageMap.get(message);
        return translated != null ? translated : message;
    }

    /**
     * 翻译消息（带模板参数）
     * <p>zh → 填充 {} 参数后返回原文，en → 查表翻译模板 + 填充参数后返回</p>
     *
     * @param template 消息模板（中文原文，含 {} 占位符）
     * @param args     模板参数
     * @return 翻译并填充参数后的消息
     */
    public String t(String template, Object... args) {
        if (template == null) return null;
        if (args == null || args.length == 0) return t(template);

        // 查表翻译模板
        String translatedTemplate = isZh ? template : messageMap.getOrDefault(template, template);

        // 填充 {} 占位符
        return fillPlaceholders(translatedTemplate, args);
    }

    // ==================== 静态便捷方法 ====================

    /**
     * 静态便捷方法：获取 I18nService 实例
     *
     * @return I18nService 实例
     */
    private static I18nService getInstance() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        return plugin != null ? plugin.getI18nService() : null;
    }

    /**
     * 静态便捷方法：判断当前是否为中文模式
     *
     * @return true=中文模式
     */
    public static boolean isZh() {
        I18nService i18n = getInstance();
        return i18n == null || i18n.isChinese();
    }

    /**
     * 静态便捷方法：翻译消息（无参数）
     *
     * @param message 消息文本（中文原文）
     * @return 翻译后的消息
     */
    public static String tr(String message) {
        I18nService i18n = getInstance();
        return i18n != null ? i18n.t(message) : message;
    }

    /**
     * 静态便捷方法：翻译消息（带模板参数）
     *
     * @param template 消息模板（中文原文，含 {} 占位符）
     * @param args     模板参数
     * @return 翻译并填充参数后的消息
     */
    public static String tr(String template, Object... args) {
        I18nService i18n = getInstance();
        return i18n != null ? i18n.t(template, args) : fillPlaceholders(template, args);
    }

    /**
     * 静态便捷方法：翻译模块名
     *
     * @param module 模块名（中文原文）
     * @return 翻译后的模块名
     */
    public static String trModule(String module) {
        I18nService i18n = getInstance();
        return i18n != null ? i18n.tModule(module) : module;
    }

    // ==================== 内部方法 ====================

    /**
     * 填充 {} 占位符（按顺序替换）
     *
     * @param template 模板字符串
     * @param args     参数数组
     * @return 填充后的字符串
     */
    static String fillPlaceholders(String template, Object... args) {
        if (template == null || args == null || args.length == 0) return template;

        StringBuilder sb = new StringBuilder(template.length() + 64);
        int argIndex = 0;
        int i = 0;
        int len = template.length();

        while (i < len) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < len && template.charAt(i + 1) == '}') {
                // 找到 {} 占位符
                if (argIndex < args.length) {
                    sb.append(args[argIndex] != null ? args[argIndex].toString() : "null");
                    argIndex++;
                } else {
                    sb.append("{}");  // 参数不够，保留占位符
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }

        return sb.toString();
    }

    /**
     * 从 YML 文件加载翻译映射
     * <p>使用 SnakeYAML 直接解析，避免 Bukkit MemorySection 将键中的 '.' 视为路径分隔符
     * 导致空路径异常（如键包含 "..." 等连续点号时）</p>
     *
     * @param file 翻译包文件
     */
    private void loadTranslationFile(File file) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream is = Files.newInputStream(file.toPath())) {
                root = yaml.load(is);
            }

            if (root == null) {
                this.moduleMap = Collections.emptyMap();
                this.messageMap = Collections.emptyMap();
                return;
            }

            // 加载模块名翻译
            Map<String, String> modules = new LinkedHashMap<>();
            Object modulesObj = root.get("modules");
            if (modulesObj instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) modulesObj).entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        modules.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }

            // 加载消息翻译
            Map<String, String> messages = new LinkedHashMap<>();
            Object messagesObj = root.get("messages");
            if (messagesObj instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) messagesObj).entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        messages.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }

            // 原子替换引用（线程安全）
            this.moduleMap = Collections.unmodifiableMap(modules);
            this.messageMap = Collections.unmodifiableMap(messages);

        } catch (Exception e) {
            PluginLogger.error("国际化", I18nService.tr("加载翻译包失败: {}", file.getPath()), e);
            this.moduleMap = Collections.emptyMap();
            this.messageMap = Collections.emptyMap();
        }
    }
}
