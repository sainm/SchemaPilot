package org.sainm.schemapilot.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record KnowledgeSearchRequest(
        @NotBlank
        @Size(max = 1000)
        String query,
        Map<String, String> metadata,
        @Positive
        Integer limit
) {
    int safeLimit() {
        return limit == null ? 5 : Math.min(limit, 20);
    }

    Map<String, String> safeMetadata() {
        return metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
