package com.zm.kilacraftAI.llm;

/**
 * 推理模型配置值对象（不可变）
 *
 * <p>由 {@link com.zm.kilacraftAI.config.AdminConfigManager} 构建，
 * 传递给需要调用推理模型的组件（如 ServerHealthGuardian）。</p>
 *
 * @param apiUrl         推理模型 API 地址
 * @param apiKey         推理模型 API 密钥
 * @param model          推理模型名称
 * @param maxTokens      最大返回 token 数
 * @param timeoutSeconds 超时时间（秒）
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public record ThinkingModelConfig(String apiUrl, String apiKey, String model, int maxTokens, int timeoutSeconds) {

    /**
     * 隐藏 API Key，防止日志泄露
     */
    @Override
    public String toString() {
        return "ThinkingModelConfig{apiUrl='%s', apiKey='***', model='%s', maxTokens=%d, timeoutSeconds=%d}".formatted(apiUrl, model, maxTokens, timeoutSeconds);
    }
}
