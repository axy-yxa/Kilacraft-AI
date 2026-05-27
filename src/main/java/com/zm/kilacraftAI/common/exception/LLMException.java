package com.zm.kilacraftAI.common.exception;

/**
 * LLM 请求异常
 *
 * <p>用于推理模型调用等场景的统一异常类型。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-19
 */
public class LLMException extends Exception {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
