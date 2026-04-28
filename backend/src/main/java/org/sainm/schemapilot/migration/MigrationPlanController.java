package org.sainm.schemapilot.migration;

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
@RequestMapping("/api/migration-plans")
public class MigrationPlanController {
    private final MigrationPlanService service;

    public MigrationPlanController(MigrationPlanService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<MigrationPlan> create(@Valid @RequestBody CreateMigrationPlanRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/{planId}")
    public ApiResponse<MigrationPlan> get(@PathVariable UUID planId) {
        return ApiResponse.ok(service.get(planId));
    }

    @PostMapping("/{planId}/execute")
    public ApiResponse<MigrationPlan> execute(@PathVariable UUID planId) {
        return ApiResponse.ok(service.execute(planId));
    }

    @PostMapping("/{planId}/steps/{stepId}/retry")
    public ApiResponse<MigrationPlan> retry(@PathVariable UUID planId, @PathVariable UUID stepId) {
        return ApiResponse.ok(service.retryStep(planId, stepId));
    }
}
