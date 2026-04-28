package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RiskExplanationRequest(
        @NotBlank
        String riskType,
        @NotBlank
        String objectType,
        @NotBlank
        @Size(max = 4000)
        String message,
        @Size(max = 4000)
        String originalSql
) {
}
