package org.sainm.schemapilot.technicalspike;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/technical-spikes")
public class TechnicalSpikeController {
    private final TechnicalSpikeService service;

    public TechnicalSpikeController(TechnicalSpikeService service) {
        this.service = service;
    }

    @PostMapping("/pgvector/probe")
    public ApiResponse<PgVectorProbeResult> probePgVector(@Valid @RequestBody PgVectorProbeRequest request) {
        return ApiResponse.ok(service.probePgVector(request));
    }

    @GetMapping("/spring-ai/pgvector-readiness")
    public ApiResponse<SpringAiPgVectorReadiness> springAiPgVectorReadiness() {
        return ApiResponse.ok(service.springAiPgVectorReadiness());
    }

    @GetMapping("/spring-ai/mcp-sdk-readiness")
    public ApiResponse<McpSdkReadiness> mcpSdkReadiness() {
        return ApiResponse.ok(service.mcpSdkReadiness());
    }
}
