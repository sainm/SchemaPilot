package org.sainm.schemapilot.agent;

import java.util.UUID;

public interface AgentRuntime {
    AgentRun run(AgentRunRequest request);

    AgentRun get(UUID runId);
}
