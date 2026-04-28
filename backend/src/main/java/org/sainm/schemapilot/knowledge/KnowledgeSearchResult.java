package org.sainm.schemapilot.knowledge;

public record KnowledgeSearchResult(
        KnowledgeChunk chunk,
        int score,
        String excerpt
) {
}
