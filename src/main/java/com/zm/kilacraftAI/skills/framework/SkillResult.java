package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.config.I18nService;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Skill 执行结果
 *
 * <p>封装 Skill 执行的返回结果</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
@Getter
public class SkillResult {

    /**
     * 是否执行成功
     * true=成功，false=失败
     */
    private final boolean success;

    /**
     * 消息内容
     */
    private final String message;

    /**
     * 数据对象（约定为 Map<String, Object> 类型，供多步骤任务参数传递）
     */
    private final Object data;

    /**
     * 创建成功的结果（无数据）
     *
     * @param message 成功消息
     * @return 成功结果
     */
    public static SkillResult success(String message) {
        return new SkillResult(true, I18nService.tr(message), null);
    }

    /**
     * 创建成功的结果（带数据）
     *
     * @param message 成功消息
     * @param data    返回数据
     * @return 成功结果
     */
    public static SkillResult success(String message, Object data) {
        return new SkillResult(true, I18nService.tr(message), data);
    }

    /**
     * 创建失败的结果
     *
     * @param message 失败消息
     * @return 失败结果
     */
    public static SkillResult failure(String message) {
        return new SkillResult(false, I18nService.tr(message), null);
    }

    /**
     * 创建失败的结果（带异常）
     *
     * @param message 失败消息
     * @param error   异常对象
     * @return 失败结果
     */
    public static SkillResult failure(String message, Throwable error) {
        return new SkillResult(false, I18nService.tr(message) + " (" + error.getMessage() + ")", null);
    }

    public SkillResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * 获取泛型数据
     *
     * @param <T> 数据类型
     * @return 转换后的数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> clazz) {
        if (clazz.isInstance(data)) {
            return (T) data;
        }
        return null;
    }

    /**
     * 获取数据 Map（便捷方法）
     * <p>多步骤任务参数传递约定： data 应为 Map<String, Object> 类型，</p>
     * <p>第三方 Skill 开发者应使用此方法获取 data Map 以确保类型安全。</p>
     *
     * @return data Map，如果 data 不是 Map 类型则返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataMap() {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return null;
    }

    /**
     * 转换为 CompletableFuture
     *
     * @return 完成的 Future
     */
    public CompletableFuture<SkillResult> toFuture() {
        return CompletableFuture.completedFuture(this);
    }
}
