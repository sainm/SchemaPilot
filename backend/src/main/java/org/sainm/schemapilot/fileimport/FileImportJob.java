package org.sainm.schemapilot.fileimport;

import org.sainm.schemapilot.sql.SqlAnalysisResponse;

import java.time.Instant;
import java.util.UUID;

public record FileImportJob(
        UUID id,
        String fileName,
        String checksumSha256,
        String encoding,
        long sizeBytes,
        FileImportStatus status,
        int progressPercent,
        SqlAnalysisResponse analysis,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
