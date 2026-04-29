package org.sainm.schemapilot.rule;

import jakarta.validation.constraints.NotBlank;

public record RuleReapplyRequest(
        String objectType,
        String riskType,
        @NotBlank
        String sql
) {
}
