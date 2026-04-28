package org.sainm.schemapilot.knowledge;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
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
