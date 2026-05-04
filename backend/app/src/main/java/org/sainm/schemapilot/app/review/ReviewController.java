package org.sainm.schemapilot.app.review;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.convert.SqlVersionService;
import org.sainm.schemapilot.review.ReviewDecision;
import org.sainm.schemapilot.review.ReviewRecord;
import org.sainm.schemapilot.review.ReviewRepository;
import org.sainm.schemapilot.review.ReviewService;
import org.sainm.schemapilot.review.SubmitReviewCommand;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/reviews")
class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final SqlVersionService sqlVersionService;

    ReviewController(
            ReviewRepository reviewRepository,
            ReviewService reviewService,
            SqlVersionService sqlVersionService) {
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.sqlVersionService = sqlVersionService;
    }

    @GetMapping
    ApiResponse<List<ReviewRecord>> listReviews(@PathVariable UUID projectId) {
        return ApiResponse.success(reviewRepository.findReviews(projectId));
    }

    @PostMapping
    ApiResponse<ReviewRecord> submitReview(
            @PathVariable UUID projectId,
            @RequestBody SubmitReviewRequest request) {
        ReviewRecord record = reviewService.submitReview(new SubmitReviewCommand(
                projectId,
                request.reportId(),
                request.decision(),
                request.reviewer(),
                request.comment()));
        if (record.decision() == ReviewDecision.APPROVED || record.decision() == ReviewDecision.CONDITIONALLY_APPROVED) {
            sqlVersionService.freezeProjectBaseline(projectId);
        }
        return ApiResponse.success(record);
    }
}
