package org.sainm.schemapilot.agent;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentRuntime runtime;

    public AgentController(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    @PostMapping("/run")
    public ApiResponse<AgentRun> run(@Valid @RequestBody AgentRunRequest request) {
        return ApiResponse.ok(runtime.run(request));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<AgentRun> get(@PathVariable UUID runId) {
        return ApiResponse.ok(runtime.get(runId));
    }
}
