package com.zm.kilacraftAI.model.knowledge;

import lombok.Getter;

/**
 * 知识片段及其相关性得分
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
@Getter
public class KnowledgeChunk {

    private final String fileName;
    private final String content;
    private final double score;

    public KnowledgeChunk(String fileName, String content, double score) {
        this.fileName = fileName;
        this.content = content;
        this.score = score;
    }
}
