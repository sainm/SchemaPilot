package org.sainm.schemapilot.ai;

import java.util.Map;

public record AiUsageStats(
        long requestCount,
        long estimatedInputTokens,
        long estimatedOutputTokens,
        double estimatedCostUsd,
        Map<String, Long> promptCounts
) {
}
