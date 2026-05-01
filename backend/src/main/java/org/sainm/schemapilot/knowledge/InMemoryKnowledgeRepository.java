package org.sainm.schemapilot.knowledge;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "schemapilot.knowledge.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryKnowledgeRepository implements KnowledgeRepository {
    private final CopyOnWriteArrayList<KnowledgeChunk> chunks = new CopyOnWriteArrayList<>();

    @Override
    public KnowledgeChunk save(KnowledgeChunk chunk) {
        chunks.add(chunk);
        return chunk;
    }

    @Override
    public List<KnowledgeChunk> findAll() {
        return List.copyOf(chunks);
    }
}
