package org.sainm.schemapilot.app;

import java.time.Instant;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

    @GetMapping("/api/health")
    ApiResponse<HealthStatus> health() {
        return ApiResponse.success(new HealthStatus("UP", "schemapilot-backend", Instant.now()));
    }

    record HealthStatus(String status, String service, Instant checkedAt) {
    }
}
