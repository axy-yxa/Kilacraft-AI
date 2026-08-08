package com.zm.kilacraftAI.common.exception;

/**
 * 搜索异常（HTTP 错误 / 网络异常），由 WebSearchSkill 的 exceptionally 捕获转 SkillResult.failure
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
