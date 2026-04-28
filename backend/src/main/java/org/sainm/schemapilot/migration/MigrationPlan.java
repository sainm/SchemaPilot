package org.sainm.schemapilot.migration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MigrationPlan(
        UUID id,
        UUID snapshotId,
        UUID targetDataSourceId,
        String reportVersion,
        MigrationPlanStatus status,
        List<MigrationPlanStep> steps,
        List<String> executionLog,
        Instant createdAt,
        Instant updatedAt
) {
}
