package org.sainm.schemapilot.review;

import java.time.Instant;
import java.util.UUID;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.report.ReportStatus;

public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;

    public ReviewService(ReviewRepository reviewRepository, ReportRepository reportRepository) {
        this.reviewRepository = reviewRepository;
        this.reportRepository = reportRepository;
    }

    public ReviewRecord submitReview(SubmitReviewCommand command) {
        if (command.reportId() == null) {
            throw new BadRequestException("Review reportId is required");
        }
        if (command.decision() == null) {
            throw new BadRequestException("Review decision is required");
        }
        org.sainm.schemapilot.report.StageReport report = reportRepository.findStageReport(command.reportId())
                .filter(stageReport -> stageReport.projectId().equals(command.projectId()))
                .orElseThrow(() -> new NotFoundException("Report not found"));
        if (isApproval(command.decision()) && report.status() == ReportStatus.STALE) {
            throw new BadRequestException("Stale report cannot be approved");
        }
        if (isApproval(command.decision()) && report.status() == ReportStatus.BLOCKED) {
            throw new BadRequestException("Blocked report cannot be approved");
        }
        ReviewRecord record = new ReviewRecord(
                UUID.randomUUID(),
                command.projectId(),
                command.reportId(),
                command.decision(),
                normalizeReviewer(command.reviewer()),
                command.comment() == null ? "" : command.comment(),
                Instant.now());
        reviewRepository.save(record);
        return record;
    }

    private boolean isApproval(ReviewDecision decision) {
        return decision == ReviewDecision.APPROVED || decision == ReviewDecision.CONDITIONALLY_APPROVED;
    }

    private String normalizeReviewer(String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            return "developer";
        }
        return reviewer.trim();
    }
}
