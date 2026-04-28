package org.sainm.schemapilot.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "schemapilot-backend",
                "status", "UP",
                "javaVersion", Runtime.version().toString(),
                "virtualThreadsEnabled", true,
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime()
        ));
    }
}
