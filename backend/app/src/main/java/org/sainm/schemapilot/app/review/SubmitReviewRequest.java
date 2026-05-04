package org.sainm.schemapilot.app.review;

import java.util.UUID;

import org.sainm.schemapilot.review.ReviewDecision;

record SubmitReviewRequest(
        UUID reportId,
        ReviewDecision decision,
        String reviewer,
        String comment) {
}
