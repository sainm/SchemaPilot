package org.sainm.schemapilot.validation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidationReport(
        UUID id,
        UUID sourceDataSourceId,
        UUID targetDataSourceId,
        String sourceTable,
        String targetTable,
        ValidationStatus status,
        List<ValidationCheckResult> checks,
        List<ValidationIssue> issues,
        Instant createdAt
) {
}
