package org.sainm.schemapilot.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.report.InMemoryReportRepository;
import org.sainm.schemapilot.report.PrecheckReport;
import org.sainm.schemapilot.report.ReportStatus;
import org.sainm.schemapilot.report.StageReport;
import org.sainm.schemapilot.report.StageReportType;

class ReviewServiceTest {

    private final InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    private final InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    private final ReviewService service = new ReviewService(reviewRepository, reportRepository);

    @Test
    void submitsReviewAgainstReportSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        reportRepository.save(
                new StageReport(reportId, projectId, StageReportType.PRECHECK, ReportStatus.CURRENT, "Precheck", "{}", "<html></html>", Instant.now()),
                new PrecheckReport(UUID.randomUUID(), projectId, reportId, ReportStatus.CURRENT, 1, 0, 0, 0, 0, 1, 1));

        ReviewRecord record = service.submitReview(new SubmitReviewCommand(projectId, reportId, ReviewDecision.APPROVED, "", "ok"));

        assertThat(record.reviewer()).isEqualTo("developer");
        assertThat(reviewRepository.findReviews(projectId)).hasSize(1);
    }

    @Test
    void rejectsApprovalForBlockedReport() {
        UUID projectId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        reportRepository.save(
                new StageReport(reportId, projectId, StageReportType.PRECHECK, ReportStatus.BLOCKED, "Precheck", "{}", "<html></html>", Instant.now()),
                new PrecheckReport(UUID.randomUUID(), projectId, reportId, ReportStatus.BLOCKED, 1, 1, 0, 0, 0, 1, 1));

        assertThatThrownBy(() -> service.submitReview(new SubmitReviewCommand(projectId, reportId, ReviewDecision.APPROVED, "dba", "ok")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Blocked report");
        assertThat(reviewRepository.findReviews(projectId)).isEmpty();
    }

    @Test
    void rejectsMissingReviewDecision() {
        UUID projectId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        reportRepository.save(
                new StageReport(reportId, projectId, StageReportType.PRECHECK, ReportStatus.CURRENT, "Precheck", "{}", "<html></html>", Instant.now()),
                new PrecheckReport(UUID.randomUUID(), projectId, reportId, ReportStatus.CURRENT, 1, 0, 0, 0, 0, 1, 1));

        assertThatThrownBy(() -> service.submitReview(new SubmitReviewCommand(projectId, reportId, null, "dba", "ok")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("decision");
        assertThat(reviewRepository.findReviews(projectId)).isEmpty();
    }
}
