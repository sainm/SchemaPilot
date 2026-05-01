package org.sainm.schemapilot.knowledge;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record HistoricalCaseRequest(
        @NotBlank
        String key,
        @NotBlank
        String title,
        @NotBlank
        String content,
        Map<String, String> metadata,
        String sourceProject
) {
}
