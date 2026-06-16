package com.zm.kilacraftAI.common.exception;

/**
 * LLM 返回空响应异常（unchecked）。
 *
 * <p>当 LLM 返回 HTTP 200 但累计 content 为空时抛出。使空响应被当作可感知的失败，
 * 走错误处理路径（{@code handleError}）而非正常回复路径（{@code showResponse}），
 * 避免上层（意图识别 / 二次分析 / 历史持久化）把降级提示误认为真实回复。</p>
 *
 * <p>设为 unchecked（继承 {@link RuntimeException}）：抛出点位于
 * {@code CompletableFuture.supplyAsync} 的 {@code Supplier} 内，无法声明 checked 异常。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-16
 */
public class EmptyResponseException extends RuntimeException {

    public EmptyResponseException(String message) {
        super(message);
    }
}
