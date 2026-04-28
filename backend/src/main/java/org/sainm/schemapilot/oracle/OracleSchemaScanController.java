package org.sainm.schemapilot.oracle;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/oracle-scans")
public class OracleSchemaScanController {
    private final OracleSchemaScanService service;

    public OracleSchemaScanController(OracleSchemaScanService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<OracleScanJob> start(@Valid @RequestBody OracleScanRequest request) {
        return ApiResponse.ok(service.start(request));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<OracleScanJob> get(@PathVariable UUID jobId) {
        return ApiResponse.ok(service.get(jobId));
    }

    @GetMapping("/{jobId}/events")
    public SseEmitter events(@PathVariable UUID jobId) {
        return service.subscribe(jobId);
    }
}
