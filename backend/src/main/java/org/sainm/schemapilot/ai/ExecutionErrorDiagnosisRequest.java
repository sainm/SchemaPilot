package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;

public record ExecutionErrorDiagnosisRequest(
        @NotBlank
        String phase,
        @NotBlank
        String objectName,
        String objectType,
        String sql,
        @NotBlank
        String errorMessage
) {
}
