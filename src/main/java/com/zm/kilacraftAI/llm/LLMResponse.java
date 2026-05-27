package com.zm.kilacraftAI.llm;

/**
 * LLM 响应封装（用于推理模型场景）
 *
 * <p>包含正常回复内容、推理过程（reasoning_content）和 token 用量。</p>
 *
 * @param content          正常回复内容
 * @param reasoningContent 推理/思考过程（DeepSeek-R1 等模型返回，可能为 null）
 * @param promptTokens     输入 token 数（-1 表示未知）
 * @param completionTokens 输出 token 数（-1 表示未知）
 * @author Zm_Mmm
 * @since 2026-05-19
 */
public record LLMResponse(String content, String reasoningContent, int promptTokens, int completionTokens) {
    /**
     * 是否包含推理过程
     */
    public boolean hasReasoning() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }
}
