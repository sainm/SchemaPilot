package org.sainm.schemapilot.input;

import java.time.Instant;
import java.util.UUID;

public record InputSource(
        UUID id,
        UUID batchId,
        UUID projectId,
        UUID sourceProjectId,
        InputSourceType type,
        String name,
        String relativePath,
        String contentHash,
        long sizeBytes,
        String encoding,
        String originalText,
        Instant createdAt) {
}
