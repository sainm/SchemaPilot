package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlsqlExplanationRequest(
        @NotBlank
        String objectType,
        @NotBlank
        String objectName,
        @NotBlank
        @Size(max = 16000)
        String originalSql,
        @Size(max = 16000)
        String postgresDraft
) {
}
