package org.sainm.schemapilot.ai;

import java.time.Instant;
import java.util.UUID;

public record AiCallLog(
        UUID id,
        UUID projectId,
        String provider,
        AiSuggestionType type,
        int objectCount,
        int riskCount,
        Instant createdAt) {
}
