package org.sainm.schemapilot.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewRecord(
        UUID id,
        UUID projectId,
        UUID reportId,
        ReviewDecision decision,
        String reviewer,
        String comment,
        Instant createdAt) {
}
