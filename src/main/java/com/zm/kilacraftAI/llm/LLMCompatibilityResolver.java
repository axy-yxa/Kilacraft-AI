package com.zm.kilacraftAI.llm;

import java.util.Locale;

/**
 * 统一处理通用 LLM 配置中的历史兼容值。
 *
 * <p>仅在内存中规范化模型名称和 OpenAI Chat Completions API 地址，不修改用户配置文件。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-31
 */
public final class LLMCompatibilityResolver {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    private LLMCompatibilityResolver() {
    }

    /**
     * 将已废弃的 DeepSeek 模型名映射到当前模型名。
     *
     * @param model 配置中的模型名
     * @return 兼容后的模型名
     */
    public static String resolveModel(String model) {
        if (model == null) {
            return null;
        }

        String normalized = model.trim();
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "deepseek-chat" -> "deepseek-v4-flash";
            case "deepseek-reasoner" -> "deepseek-v4-pro";
            default -> normalized;
        };
    }

    /**
     * 将 OpenAI 兼容 API 地址补全为 Chat Completions 端点。
     *
     * @param apiUrl 配置中的 API 地址
     * @return 兼容后的 API 地址
     */
    public static String resolveApiUrl(String apiUrl) {
        if (apiUrl == null) {
            return null;
        }

        String value = apiUrl.trim();
        if (value.isEmpty()) {
            return value;
        }
        int suffixStart = firstQueryOrFragmentIndex(value);
        String endpoint = suffixStart >= 0 ? value.substring(0, suffixStart) : value;
        String suffix = suffixStart >= 0 ? value.substring(suffixStart) : "";

        endpoint = trimTrailingSlashes(endpoint);
        String lowerEndpoint = endpoint.toLowerCase(Locale.ROOT);
        if (lowerEndpoint.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            return endpoint + suffix;
        }
        if (lowerEndpoint.endsWith("/chat")) {
            return endpoint + "/completions" + suffix;
        }
        return endpoint + CHAT_COMPLETIONS_SUFFIX + suffix;
    }

    private static int firstQueryOrFragmentIndex(String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');
        if (queryIndex < 0) {
            return fragmentIndex;
        }
        if (fragmentIndex < 0) {
            return queryIndex;
        }
        return Math.min(queryIndex, fragmentIndex);
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

}
