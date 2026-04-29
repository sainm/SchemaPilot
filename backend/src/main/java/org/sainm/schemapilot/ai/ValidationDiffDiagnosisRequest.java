package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ValidationDiffDiagnosisRequest(
        @NotBlank
        String objectName,
        String checkType,
        String sourceValue,
        String targetValue,
        List<String> issueCodes
) {
}
