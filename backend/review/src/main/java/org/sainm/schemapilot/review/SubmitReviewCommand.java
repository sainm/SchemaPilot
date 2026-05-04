package org.sainm.schemapilot.review;

import java.util.UUID;

public record SubmitReviewCommand(
        UUID projectId,
        UUID reportId,
        ReviewDecision decision,
        String reviewer,
        String comment) {
}
