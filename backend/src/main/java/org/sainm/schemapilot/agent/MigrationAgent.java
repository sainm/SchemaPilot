package org.sainm.schemapilot.agent;

import java.util.Map;

interface MigrationAgent {
    AgentType type();

    Map<String, Object> execute(AgentRunRequest request, AgentStepRecorder recorder);
}
