package org.sainm.schemapilot.knowledge;

import java.util.List;
import java.util.Map;

public interface KnowledgeRepository {
    KnowledgeChunk save(KnowledgeChunk chunk);

    List<KnowledgeChunk> findAll();

    default boolean supportsVectorSearch() {
        return false;
    }

    default List<KnowledgeChunk> findNearestByEmbedding(float[] queryVector, Map<String, String> metadataFilters, int limit) {
        return List.of();
    }
}
