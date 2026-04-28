package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.model.AgentStatus;

import java.time.Instant;
import java.util.UUID;

public record AgentStep(
        UUID id,
        int sequence,
        AgentStatus status,
        String action,
        String toolName,
        String inputSummary,
        String outputSummary,
        Instant createdAt
) {
}
