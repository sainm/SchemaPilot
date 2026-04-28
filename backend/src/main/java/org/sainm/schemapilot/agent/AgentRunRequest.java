package org.sainm.schemapilot.agent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentRunRequest(
        @NotNull
        AgentType type,
        @Size(max = 2000)
        String objective,
        @Size(max = 200000)
        String sql,
        @Size(max = 4000)
        String errorMessage
) {
}
