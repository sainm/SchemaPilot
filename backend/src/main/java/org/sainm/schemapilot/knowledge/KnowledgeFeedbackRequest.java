package org.sainm.schemapilot.knowledge;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeFeedbackRequest(
        @NotBlank
        String chunkKey,
        boolean accepted
) {
}
