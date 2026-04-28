package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.model.AgentStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AgentStepRecorder {
    private final List<AgentStep> steps = new ArrayList<>();

    void step(AgentStatus status, String action, String toolName, String inputSummary, String outputSummary) {
        steps.add(new AgentStep(
                UUID.randomUUID(),
                steps.size() + 1,
                status,
                action,
                toolName,
                truncate(inputSummary),
                truncate(outputSummary),
                Instant.now()
        ));
    }

    List<AgentStep> snapshot() {
        return List.copyOf(steps);
    }

    int size() {
        return steps.size();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 240 ? value : value.substring(0, 240) + "...";
    }
}
