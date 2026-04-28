package org.sainm.schemapilot.mcp;

import java.time.Instant;
import java.util.UUID;

public record McpToolCallRecord(
        UUID id,
        String toolName,
        boolean usedDefaultDryRun,
        Boolean requestedDryRun,
        boolean allowed,
        boolean success,
        String message,
        Object result,
        Instant startedAt,
        Instant finishedAt
) {
}
