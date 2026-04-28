package org.sainm.schemapilot.migration;

import org.sainm.schemapilot.model.ObjectType;

import java.time.Instant;
import java.util.UUID;

public record MigrationPlanStep(
        UUID id,
        int sequence,
        ObjectType objectType,
        String objectName,
        UUID sqlVersionId,
        String sql,
        MigrationPlanStepStatus status,
        int attempts,
        String failureReason,
        String workItem,
        Instant updatedAt
) {
    MigrationPlanStep withStatus(MigrationPlanStepStatus nextStatus, int attempts, String failureReason, String workItem) {
        return new MigrationPlanStep(id, sequence, objectType, objectName, sqlVersionId, sql, nextStatus, attempts, failureReason, workItem, Instant.now());
    }
}
