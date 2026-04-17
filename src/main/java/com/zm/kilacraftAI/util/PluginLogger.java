package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 插件统一日志工具类
 * <p>
 * 封装所有日志输出逻辑,提供类型安全的API,支持DEBUG模式控制。
 * 所有模块必须通过此类输出日志,禁止直接使用 plugin.getLogger()。
 * </p>
 *
 * <h3>使用示例:</h3>
 * <pre>
 * // 普通日志
 * PluginLogger.info("技能系统", "已加载 5 个技能配置");
 * PluginLogger.warn("配置加载", "找不到配置文件,使用默认配置");
 * PluginLogger.error("挂机任务", "回调任务执行异常", exception);
 *
 * // DEBUG日志(自动受配置控制)
 * PluginLogger.debug("意图识别", "开始LLM意图识别,用户:" + playerName);
 * </pre>
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public final class PluginLogger {

    /**
     * 插件日志实例
     */
    private static final Logger LOGGER;

    static {
        KilacraftAI plugin = KilacraftAI.getInstance();
        LOGGER = (plugin != null) ? plugin.getLogger() : Logger.getGlobal();
    }

    /**
     * 私有构造函数,防止实例化
     */
    private PluginLogger() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== INFO 级别日志 ====================

    /**
     * 输出 INFO 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * 输出 INFO 级别日志(带模块前缀)
     *
     * @param module  模块名称(如"技能系统"、"配置加载")
     * @param message 日志消息
     */
    public static void info(String module, String message) {
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
        LOGGER.log(Level.INFO, formatMessage(module, message), throwable);
    }

    // ==================== DEBUG 级别日志 ====================

    /**
     * 输出 DEBUG 级别日志(无模块前缀)
     * <p>仅在 config.yml 中 debug_mode=true 时输出</p>
     *
     * @param message 日志消息
     */
    public static void debug(String message) {
        if (!isDebugEnabled()) {
            return;
        }
        LOGGER.info("[DEBUG] " + message);
    }

    /**
     * 输出 DEBUG 级别日志(带模块前缀)
     * <p>仅在 config.yml 中 debug_mode=true 时输出</p>
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void debug(String module, String message) {
        if (!isDebugEnabled()) {
            return;
        }
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
        if (!isDebugEnabled()) {
            return;
        }
        LOGGER.log(Level.INFO, "[DEBUG] " + formatMessage(module, message), throwable);
    }

    // ==================== WARN 级别日志 ====================

    /**
     * 输出 WARN 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void warn(String message) {
        LOGGER.warning(message);
    }

    /**
     * 输出 WARN 级别日志(带模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void warn(String module, String message) {
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
        LOGGER.log(Level.WARNING, formatMessage(module, message), throwable);
    }

    // ==================== ERROR 级别日志 ====================

    /**
     * 输出 ERROR 级别日志(无模块前缀)
     *
     * @param message 日志消息
     */
    public static void error(String message) {
        LOGGER.severe(message);
    }

    /**
     * 输出 ERROR 级别日志(带模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     */
    public static void error(String module, String message) {
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
        LOGGER.log(Level.SEVERE, formatMessage(module, message), throwable);
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化日志消息(添加模块前缀)
     *
     * @param module  模块名称
     * @param message 日志消息
     * @return 格式化后的消息, 格式:[模块名] 消息内容
     */
    private static String formatMessage(String module, String message) {
        return "[" + module + "] " + message;
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
}
