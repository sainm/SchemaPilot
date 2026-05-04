package org.sainm.schemapilot.input;

import java.time.Instant;
import java.util.UUID;

public record InputBatch(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        InputBatchStatus status,
        Instant createdAt) {
}
