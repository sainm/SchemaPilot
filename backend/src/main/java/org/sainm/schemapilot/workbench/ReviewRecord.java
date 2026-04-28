package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.model.ReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewRecord(
        UUID id,
        UUID snapshotId,
        String reportVersion,
        ReviewDecision decision,
        ReportStatus resultingReportStatus,
        String reviewer,
        String comment,
        List<UUID> boundSqlVersionIds,
        Instant reviewedAt
) {
}
