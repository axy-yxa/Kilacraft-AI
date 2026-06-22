package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.i18n.I18nService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 插件统一日志工具类
 * <p>
 * 封装所有日志输出逻辑,提供类型安全的API,支持DEBUG模式控制。
 * 所有模块必须通过此类输出日志,禁止直接使用 plugin.getLogger()。
 * </p>
 *
 * <h3>国际化 (i18n) 使用规范 —— 必读：</h3>
 * <p>所有日志方法已内置 I18nService 翻译，调用方无需手动翻译。但必须遵守以下规则：</p>
 * <ul>
 *   <li><b>静态内容</b>（无动态参数）：使用任意重载即可，翻译会自动生效。
 *       <pre>PluginLoggerUtil.info("技能配置", "已加载 5 个技能配置");  // 自动翻译</pre>
 *   </li>
 *   <li><b>动态内容</b>（含变量）：<b>必须</b>使用模板重载（带 {@code Object... args} 的版本），
 *       用 {@code {}} 占位符代替字符串拼接。否则翻译表无法匹配拼接后的完整字符串。
 *       <pre>
 * // 错误：拼接后的字符串无法匹配翻译表的模板 key
 * PluginLoggerUtil.info("技能配置", "已加载 " + count + " 个技能配置");
 *
 * // 正确：模板重载会先查表翻译模板，再填充参数
 * PluginLoggerUtil.info("技能配置", "已加载 {} 个技能配置", count);
 *       </pre>
 *   </li>
 *   <li><b>动态内容 + 异常</b>：先外部调用 {@code I18nService.tr()} 翻译模板，
 *       再传入带 Throwable 的重载。不可用模板重载 + Throwable（会被当作模板参数）。
 *       <pre>
 * // 错误：exception 会被当作 {} 的参数，丢失堆栈
 * PluginLoggerUtil.error("模块", "加载失败: {}", errorMsg, exception);
 *
 * // 正确：先翻译模板，再传入 Throwable 重载
 * PluginLoggerUtil.error("模块", I18nService.tr("加载失败: {}", errorMsg), exception);
 *       </pre>
 *   </li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public final class PluginLoggerUtil {

    /**
     * 插件日志实例（延迟初始化）
     */
    private static Logger LOGGER;

    static {
        // 延迟初始化：避免类加载时插件实例尚未就绪
        // LOGGER 在首次使用时通过 getLogger() 动态获取
    }

    /**
     * 获取日志实例（延迟初始化）
     */
    private static Logger getLogger() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        return (plugin != null) ? plugin.getLogger() : Logger.getGlobal();
    }

    /**
     * 私有构造函数,防止实例化
     */
    private PluginLoggerUtil() {
    }

    /**
     * 输出 INFO 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void info(String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info(I18nService.tr(message));
    }

    /**
     * 输出 INFO 级别日志(带模块前缀)
     *
     * @param module  模块名称(如"技能系统"、"配置加载")
     * @param message 日志消息
     */
    public static void info(String module, String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info(formatMessage(module, message));
    }

    /**
     * 输出 INFO 级别日志(带异常堆栈)
     *
     * @param module    模块名称
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void info(String module, String message, Throwable throwable) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.log(Level.INFO, formatMessage(module, message), throwable);
    }

    /**
     * 输出 DEBUG 级别日志(无模块前缀)
     * <p>仅在 config.yml 中 debug_mode=true 时输出</p>
     *
     * @param message 日志消息
     */
    public static void debug(String message) {
        if (!isDebugEnabled()) return;
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info("[DEBUG] " + I18nService.tr(message));
    }

    /**
     * 输出 DEBUG 级别日志(带模块前缀)
     * <p>仅在 config.yml 中 debug_mode=true 时输出</p>
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void debug(String module, String message) {
        if (!isDebugEnabled()) return;
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info("[DEBUG] " + formatMessage(module, message));
    }

    /**
     * 输出 DEBUG 级别日志(带异常堆栈)
     * <p>仅在 config.yml 中 debug_mode=true 时输出</p>
     *
     * @param module    模块名称
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void debug(String module, String message, Throwable throwable) {
        if (!isDebugEnabled()) return;
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.log(Level.INFO, "[DEBUG] " + formatMessage(module, message), throwable);
    }

    /**
     * 输出 WARN 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void warn(String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.warning(I18nService.tr(message));
    }

    /**
     * 输出 WARN 级别日志(带模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void warn(String module, String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.warning(formatMessage(module, message));
    }

    /**
     * 输出 WARN 级别日志(带异常堆栈)
     *
     * @param module    模块名称
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void warn(String module, String message, Throwable throwable) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.log(Level.WARNING, formatMessage(module, message), throwable);
    }

    /**
     * 输出 ERROR 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void error(String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.severe(I18nService.tr(message));
    }

    /**
     * 输出 ERROR 级别日志(带模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void error(String module, String message) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.severe(formatMessage(module, message));
    }

    /**
     * 输出 ERROR 级别日志(带异常堆栈)
     *
     * @param module    模块名称
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void error(String module, String message, Throwable throwable) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.log(Level.SEVERE, formatMessage(module, message), throwable);
    }

    /**
     * 格式化日志消息(添加模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     * @return 格式化后的消息, 格式:[模块名] 消息内容
     */
    private static String formatMessage(String module, String message) {
        return "[" + I18nService.trModule(module) + "] " + I18nService.tr(message);
    }

    /**
     * 构建模块前缀（仅翻译模块名，不翻译消息）。
     *
     * <p>供模板重载使用——消息已由调用方经 {@code tr(template, args)} 翻译并填充，
     * 此处仅拼前缀，避免 {@link #formatMessage} 对已翻译消息二次 tr（原实现的双重翻译根因）。</p>
     */
    private static String modulePrefix(String module) {
        return "[" + I18nService.trModule(module) + "] ";
    }

    /**
     * 检查DEBUG模式是否启用
     *
     * @return true=启用DEBUG日志, false=禁用
     */
    private static boolean isDebugEnabled() {
        try {
            KilacraftAI plugin = KilacraftAI.getInstance();
            return plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode();
        } catch (Exception e) {
            // 插件未初始化或配置管理器不可用时,默认禁用DEBUG
            return false;
        }
    }

    /**
     * 输出 INFO 级别日志（带模块前缀 + 模板参数）
     *
     * @param module   模块名称
     * @param template 消息模板（含 {} 占位符）
     * @param args     模板参数
     */
    public static void info(String module, String template, Object... args) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info(modulePrefix(module) + I18nService.tr(template, args));
    }

    /**
     * 输出 DEBUG 级别日志（带模块前缀 + 模板参数）
     *
     * @param module   模块名称
     * @param template 消息模板（含 {} 占位符）
     * @param args     模板参数
     */
    public static void debug(String module, String template, Object... args) {
        if (!isDebugEnabled()) return;
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.info("[DEBUG] " + modulePrefix(module) + I18nService.tr(template, args));
    }

    /**
     * 输出 WARN 级别日志（带模块前缀 + 模板参数）
     *
     * @param module   模块名称
     * @param template 消息模板（含 {} 占位符）
     * @param args     模板参数
     */
    public static void warn(String module, String template, Object... args) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.warning(modulePrefix(module) + I18nService.tr(template, args));
    }

    /**
     * 输出 ERROR 级别日志（带模块前缀 + 模板参数）
     *
     * @param module   模块名称
     * @param template 消息模板（含 {} 占位符）
     * @param args     模板参数
     */
    public static void error(String module, String template, Object... args) {
        if (LOGGER == null) LOGGER = getLogger();
        LOGGER.severe(modulePrefix(module) + I18nService.tr(template, args));
    }
}
