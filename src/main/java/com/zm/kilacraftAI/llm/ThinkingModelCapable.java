package com.zm.kilacraftAI.llm;

/**
 * 标记接口：表示此 LLMProvider 支持推理/思考模型功能
 *
 * <p>AdminConfigManager 通过 {@code instanceof ThinkingModelCapable} 判断当前 Provider
 * 是否可以创建推理模型专用 HTTP 客户端（共享连接池 + 更长超时）。</p>
 *
 * <p>目前仅 {@link GenericLLMProvider} 实现此接口。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-19
 */
public interface ThinkingModelCapable {
}
