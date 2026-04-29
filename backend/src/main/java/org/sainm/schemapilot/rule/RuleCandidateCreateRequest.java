package org.sainm.schemapilot.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RuleCandidateCreateRequest(
        @NotNull
        RuleCandidateSource source,
        String sourceProject,
        String objectType,
        String riskType,
        @NotBlank
        String originalSql,
        @NotBlank
        String revisedSql
) {
}
