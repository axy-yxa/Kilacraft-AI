package com.zm.kilacraftAI.skills.framework.task;

/**
 * 占位符解析结果
 */
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
    
    public String getResolvedValue() {
        return resolvedValue;
    }
    
    public String getFailedPlaceholder() {
        return failedPlaceholder;
    }
}
