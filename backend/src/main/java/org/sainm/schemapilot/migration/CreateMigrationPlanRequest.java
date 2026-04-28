package org.sainm.schemapilot.migration;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMigrationPlanRequest(
        @NotNull
        UUID snapshotId,
        @NotNull
        UUID targetDataSourceId
) {
}
