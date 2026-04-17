package com.zm.kilacraftAI.knowledge;

import lombok.Getter;

/**
 * 知识片段及其相关性得分
 *
 * <p>用于存储分段后的知识内容及其与查询的相关性评分</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
@Getter
public class KnowledgeChunk {

    private final String fileName;
    private final String content;
    private final double score;

    /**
     * 创建知识片段
     *
     * @param fileName 来源文件名
     * @param content  片段内容
     * @param score    相关性得分
     */
    public KnowledgeChunk(String fileName, String content, double score) {
        this.fileName = fileName;
        this.content = content;
        this.score = score;
    }
}
