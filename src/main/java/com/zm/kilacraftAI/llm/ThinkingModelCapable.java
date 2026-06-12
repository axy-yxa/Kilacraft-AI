package com.zm.kilacraftAI.llm;

import com.zm.kilacraftAI.common.exception.LLMException;
import okhttp3.OkHttpClient;

/**
 * 推理/思考模型能力接口
 *
 * <p>支持推理模型的 LLMProvider 实现此接口，提供推理模型专用请求能力和 HTTP 客户端共享能力。</p>
 *
 * <p>调用方通过 {@code instanceof ThinkingModelCapable} 检查能力，
 * 无需向下转型到具体 Provider 实现类。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-19
 */
public interface ThinkingModelCapable {

    /**
     * 获取共享的 HTTP 客户端
     *
     * <p>用于创建派生客户端（如推理模型专用客户端），共享连接池但使用不同的超时配置。</p>
     *
     * @return 共享的 OkHttpClient 实例
     */
    OkHttpClient getSharedHttpClient();

    /**
     * 执行推理模型请求（非流式）
     *
     * <p>专用于服务器诊断等场景。自动适配 DeepSeek-R1 / OpenAI o1-o3-o4 等不同厂商的请求格式。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param config       推理模型配置（API 地址、密钥、模型名等）
     * @param client       推理模型专用 HTTP 客户端
     * @return LLM 响应（含推理过程和用量）
     * @throws LLMException 请求或解析失败时抛出
     */
    LLMResponse processRequestWithThinkingModel(String systemPrompt, String userMessage, ThinkingModelConfig config, OkHttpClient client) throws LLMException;
}
