package org.sainm.schemapilot.ai;

import java.util.List;

public record HistoricalRuleTemplateRequest(
        String objectType,
        List<String> riskTypes,
        List<String> historicalCases
) {
}
