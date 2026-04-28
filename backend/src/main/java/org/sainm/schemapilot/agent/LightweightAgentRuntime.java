package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.model.AgentStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LightweightAgentRuntime implements AgentRuntime {
    private final List<MigrationAgent> agents;
    private final Map<UUID, AgentRun> runs = new ConcurrentHashMap<>();

    public LightweightAgentRuntime(List<MigrationAgent> agents) {
        this.agents = agents;
    }

    @Override
    public AgentRun run(AgentRunRequest request) {
        var now = Instant.now();
        var runId = UUID.randomUUID();
        var recorder = new AgentStepRecorder();
        var run = new AgentRun(
                runId,
                request.type(),
                AgentStatus.CREATED,
                request.objective() == null || request.objective().isBlank() ? defaultObjective(request.type()) : request.objective(),
                recorder.snapshot(),
                Map.of(),
                now,
                now
        );
        runs.put(runId, run);

        try {
            recorder.step(AgentStatus.PLANNING, "Build guarded migration plan", null, request.type().name(), "Agent will only call allowlisted local tools.");
            var result = agentFor(request.type()).execute(request, recorder);
            recorder.step(AgentStatus.AUDITED, "Record audit trail", null, "steps=" + recorder.size(), "Run is waiting for a human decision.");
            run = new AgentRun(runId, request.type(), AgentStatus.AWAITING_USER_DECISION, run.objective(), recorder.snapshot(), result, run.createdAt(), Instant.now());
        } catch (RuntimeException ex) {
            recorder.step(AgentStatus.FAILED, "Capture failure", null, request.type().name(), ex.getMessage());
            run = new AgentRun(runId, request.type(), AgentStatus.FAILED, run.objective(), recorder.snapshot(), Map.of("error", ex.getMessage()), run.createdAt(), Instant.now());
        }
        runs.put(runId, run);
        return run;
    }

    @Override
    public AgentRun get(UUID runId) {
        var run = runs.get(runId);
        if (run == null) {
            throw new NotFoundException("Agent run not found: " + runId);
        }
        return run;
    }

    private MigrationAgent agentFor(AgentType type) {
        return agents.stream()
                .filter(agent -> agent.type() == type)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Agent type not registered: " + type));
    }

    private String defaultObjective(AgentType type) {
        return switch (type) {
            case ASSESSMENT -> "Assess Oracle SQL migration risk.";
            case CONVERSION -> "Create a PostgreSQL conversion draft.";
            case ERROR_DIAGNOSIS -> "Diagnose a migration error.";
        };
    }
}
