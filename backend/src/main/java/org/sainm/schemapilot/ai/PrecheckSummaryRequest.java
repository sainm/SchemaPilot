package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record PrecheckSummaryRequest(
        @PositiveOrZero
        int statementCount,
        @PositiveOrZero
        int riskCount,
        @PositiveOrZero
        int highRiskCount,
        @PositiveOrZero
        int blockerCount,
        List<String> topRiskTypes
) {
    public List<String> topRiskTypes() {
        return topRiskTypes == null ? List.of() : List.copyOf(topRiskTypes);
    }
}
