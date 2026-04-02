package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.skills.framework.SkillContext;

/**
 * 步骤上下文构建结果
 */
public class BuildContextResult {
    final SkillContext context;
    final String errorMessage; // 如果构建失败，记录错误信息
    
    public BuildContextResult(SkillContext context, String errorMessage) {
        this.context = context;
        this.errorMessage = errorMessage;
    }
    
    public boolean isFailed() {
        return errorMessage != null;
    }
    
    public SkillContext getContext() {
        return context;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
