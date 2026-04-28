package org.sainm.schemapilot.knowledge;

import java.util.List;

public interface KnowledgeRepository {
    KnowledgeChunk save(KnowledgeChunk chunk);

    List<KnowledgeChunk> findAll();
}
