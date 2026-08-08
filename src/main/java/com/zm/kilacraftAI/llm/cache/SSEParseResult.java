package com.zm.kilacraftAI.llm.cache;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * SSE 流式解析结果，包含完整响应文本和最后一个 chunk 的 usage 对象。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public record SSEParseResult(String content, @Nullable JsonObject usage) {

}
