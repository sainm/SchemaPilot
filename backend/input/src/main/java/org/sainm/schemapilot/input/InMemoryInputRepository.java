package org.sainm.schemapilot.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryInputRepository implements InputRepository {

    private final ConcurrentMap<UUID, InputBatch> batches = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, InputSource> sources = new ConcurrentHashMap<>();

    @Override
    public void saveBatch(InputBatch batch) {
        batches.put(batch.id(), batch);
    }

    @Override
    public void saveSource(InputSource source) {
        sources.put(source.id(), source);
    }

    @Override
    public List<InputBatch> findBatches(UUID projectId) {
        return batches.values().stream()
                .filter(batch -> batch.projectId().equals(projectId))
                .sorted(Comparator.comparing(InputBatch::createdAt))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public List<InputSource> findSources(UUID batchId) {
        return sources.values().stream()
                .filter(source -> source.batchId().equals(batchId))
                .sorted(Comparator.comparing(InputSource::createdAt))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
