package org.sainm.schemapilot.rule;

import jakarta.validation.constraints.NotBlank;

public record RuleCandidateReviewRequest(
        @NotBlank
        String reviewer,
        boolean approved
) {
}
