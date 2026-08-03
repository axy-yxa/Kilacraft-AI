package com.zm.kilacraftAI.skills.framework.task;

import lombok.Getter;

/**
 * 占位符解析结果
 *
 * @author Zm_Mmm
 * @since 2026-04-02
 */
@Getter
public class PlaceholderResolveResult {
    final String resolvedValue;
    final String failedPlaceholder; // 如果解析失败，记录失败的占位符

    public PlaceholderResolveResult(String resolvedValue, String failedPlaceholder) {
        this.resolvedValue = resolvedValue;
        this.failedPlaceholder = failedPlaceholder;
    }

    public boolean isFailed() {
        return failedPlaceholder != null;
    }

}
