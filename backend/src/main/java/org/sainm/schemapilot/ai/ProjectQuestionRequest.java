package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ProjectQuestionRequest(
        @NotBlank
        String question,
        String projectSummary,
        List<String> topRiskTypes,
        List<String> objectNames
) {
}
