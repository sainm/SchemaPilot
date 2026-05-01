package org.sainm.schemapilot.oracle;

import java.time.Instant;
import java.util.UUID;

public record OracleScanJob(
        UUID id,
        UUID dataSourceId,
        String schemaName,
        OracleScanStatus status,
        int progressPercent,
        String stage,
        OracleScanSnapshot snapshot,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
