package org.sainm.schemapilot.report;

import java.time.Instant;
import java.util.UUID;

public record StageReport(
        UUID id,
        UUID projectId,
        StageReportType type,
        ReportStatus status,
        String title,
        String jsonContent,
        String htmlContent,
        Instant createdAt) {
}
