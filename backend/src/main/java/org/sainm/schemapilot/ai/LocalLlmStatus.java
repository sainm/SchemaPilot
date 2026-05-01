package org.sainm.schemapilot.ai;

import java.util.List;

public record LocalLlmStatus(
        boolean enabled,
        String endpoint,
        String model,
        boolean reachable,
        List<String> discoveredModels,
        long timeoutMs,
        long requestCount,
        long timeoutCount,
        long failureCount,
        long fallbackCount,
        String lastError
) {
}
