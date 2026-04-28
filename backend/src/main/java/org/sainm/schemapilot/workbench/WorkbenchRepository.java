package org.sainm.schemapilot.workbench;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WorkbenchRepository {
    WorkbenchSnapshot save(WorkbenchSnapshot snapshot);

    Optional<WorkbenchSnapshot> findSnapshot(UUID snapshotId);

    Optional<SavedAiSuggestion> findSuggestion(UUID suggestionId);

    List<WorkbenchSnapshot> findAllSnapshots();
}
