package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.model.AgentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentRun(
        UUID id,
        AgentType type,
        AgentStatus status,
        String objective,
        List<AgentStep> steps,
        Map<String, Object> result,
        Instant createdAt,
        Instant updatedAt
) {
}
