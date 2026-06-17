package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Skill 执行结果
 *
 * <p>封装 Skill 执行的返回结果。</p>
 *
 * <p>结构化状态见 {@link SkillStatus}。<b>控制流</b>请使用 {@link #isSuccess()}
 * （{@link SkillStatus#SUCCESS}→true，其余→false）；<b>呈现层打标</b>由归一化层
 * 按 {@link #getStatus()} 统一处理，message 应为裸文本（不含 {@code [STATUS]} 前缀）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
@Getter
public class SkillResult {

    /**
     * 是否执行成功
     * true=成功，false=失败（含 {@link SkillStatus#NEED_INFO} / {@link SkillStatus#SKIPPED}）
     */
    private final boolean success;

    /**
     * 结构化状态（呈现层打标用，不驱动控制流）
     */
    private final SkillStatus status;

    /**
     * 消息内容（裸文本，不含 [STATUS] 前缀；前缀由归一化层统一添加）
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
        return new SkillResult(true, SkillStatus.SUCCESS, I18nService.tr(message), null);
    }

    /**
     * 创建成功的结果（带数据）
     *
     * @param message 成功消息
     * @param data    返回数据
     * @return 成功结果
     */
    public static SkillResult success(String message, Object data) {
        return new SkillResult(true, SkillStatus.SUCCESS, I18nService.tr(message), data);
    }

    /**
     * 创建失败的结果
     *
     * @param message 失败消息
     * @return 失败结果
     */
    public static SkillResult failure(String message) {
        return new SkillResult(false, SkillStatus.FAILURE, I18nService.tr(message), null);
    }

    /**
     * 创建失败的结果（带异常）
     *
     * @param message 失败消息
     * @param error   异常对象
     * @return 失败结果
     */
    public static SkillResult failure(String message, Throwable error) {
        if (error != null && error.getMessage() != null) {
            return new SkillResult(false, SkillStatus.FAILURE, I18nService.tr("{} ({})", I18nService.tr(message), error.getMessage()), null);
        }
        return new SkillResult(false, SkillStatus.FAILURE, I18nService.tr(message), null);
    }

    /**
     * 创建"需补充信息/二次确认"的结果（软失败）。
     *
     * <p>用于 skill 检测到缺参数或需玩家确认时：归一化层会输出 {@code [NEED_INFO]} marker，
     * LLM 据此触发补充/确认流程。{@link #isSuccess()} 返回 false。</p>
     *
     * <p>这是第三方 SPI skill 实现"二次确认/补全信息"的官方契约——
     * 返回本结果并给出含具体值的提示，由意图识别提示词与 skill 描述声明的确认参数协同完成确认。</p>
     *
     * @param message 需补充/确认的提示（建议含已计算的具体值）
     * @return NEED_INFO 结果
     */
    public static SkillResult needInfo(String message) {
        return new SkillResult(false, SkillStatus.NEED_INFO, I18nService.tr(message), null);
    }

    /**
     * 兼容旧调用方的公开构造器（签名保持不变，二进制兼容）。
     *
     * <p>第三方已编译 jar 可能直接 {@code new}；status 由 {@code success} 推导
     * （true→{@link SkillStatus#SUCCESS}，false→{@link SkillStatus#FAILURE}）。
     * 注意：本构造器不对 message 做国际化（与历史行为一致）。</p>
     *
     * @param success 是否成功
     * @param message 消息内容
     * @param data    数据对象
     */
    public SkillResult(boolean success, String message, Object data) {
        this(success, success ? SkillStatus.SUCCESS : SkillStatus.FAILURE, message, data);
    }

    // 包级可见：供同包内部类 SkillResultFormatter 构造 SKIPPED 等；不进 SPI 公开 API（第三方跨包不可见）
    SkillResult(boolean success, SkillStatus status, String message, Object data) {
        this.success = success;
        this.status = status;
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
