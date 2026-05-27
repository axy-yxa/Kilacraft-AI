package com.zm.kilacraftAI.i18n;

import java.util.List;

/**
 * 文本处理策略接口，用于国际化支持。
 * <p>提供语言相关的文本分词、关键词提取和搜索查询生成。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-21
 */
public interface TextProcessor {

    /**
     * 文本分词
     *
     * @param text 输入文本
     * @return 分词结果列表
     */
    List<String> segment(String text);

    /**
     * 从文本中提取关键词
     *
     * @param text 输入文本
     * @param topK 最大返回关键词数量
     * @return 关键词列表（去重、保序）
     */
    List<String> extractKeywords(String text, int topK);

    /**
     * 将文本转换为搜索查询字符串
     *
     * @param text 原始文本
     * @param topK 提取的关键词数量
     * @return 以空格分隔的关键词字符串，适用于知识库检索
     */
    String toSearchQuery(String text, int topK);

    /**
     * 使用用户自定义词初始化自定义词典
     *
     * @param customWords 自定义词汇列表
     */
    void initCustomDictionary(List<String> customWords);
}
