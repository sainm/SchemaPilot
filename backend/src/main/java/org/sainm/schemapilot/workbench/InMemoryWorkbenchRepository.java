package org.sainm.schemapilot.workbench;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWorkbenchRepository implements WorkbenchRepository {
    private final ConcurrentHashMap<UUID, WorkbenchSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public WorkbenchSnapshot save(WorkbenchSnapshot snapshot) {
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<WorkbenchSnapshot> findSnapshot(UUID snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public Optional<SavedAiSuggestion> findSuggestion(UUID suggestionId) {
        return snapshots.values().stream()
                .flatMap(snapshot -> snapshot.aiSuggestions().stream())
                .filter(suggestion -> suggestion.id().equals(suggestionId))
                .findFirst();
    }

    @Override
    public List<WorkbenchSnapshot> findAllSnapshots() {
        return List.copyOf(snapshots.values());
    }
}
