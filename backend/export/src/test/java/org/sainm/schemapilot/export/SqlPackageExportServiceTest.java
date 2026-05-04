package org.sainm.schemapilot.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.convert.InMemorySqlVersionRepository;
import org.sainm.schemapilot.convert.SqlVersion;
import org.sainm.schemapilot.convert.SqlVersionSource;
import org.sainm.schemapilot.convert.SqlVersionStatus;
import org.sainm.schemapilot.dependency.DependencyType;
import org.sainm.schemapilot.dependency.InMemoryDependencyRepository;
import org.sainm.schemapilot.dependency.ObjectDependency;
import org.sainm.schemapilot.report.InMemoryReportRepository;
import org.sainm.schemapilot.report.PrecheckReport;
import org.sainm.schemapilot.report.ReportStatus;
import org.sainm.schemapilot.report.StageReport;
import org.sainm.schemapilot.report.StageReportType;
import org.sainm.schemapilot.review.InMemoryReviewRepository;
import org.sainm.schemapilot.review.ReviewDecision;
import org.sainm.schemapilot.review.ReviewRecord;
import org.sainm.schemapilot.risk.InMemoryRiskRepository;

class SqlPackageExportServiceTest {

    private final InMemorySqlVersionRepository versionRepository = new InMemorySqlVersionRepository();
    private final InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    private final InMemoryRiskRepository riskRepository = new InMemoryRiskRepository();
    private final InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    private final InMemoryDependencyRepository dependencyRepository = new InMemoryDependencyRepository();
    private final SqlPackageExportService service = new SqlPackageExportService(versionRepository, reviewRepository, riskRepository, reportRepository, dependencyRepository);

    @Test
    void blocksExportBeforeApprovedReview() {
        UUID projectId = UUID.randomUUID();
        saveReport(projectId, ReportStatus.CURRENT, Instant.now());

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Approved review");
    }

    @Test
    void exportsFrozenBaselineAfterReview() {
        UUID projectId = UUID.randomUUID();
        saveBaseline(projectId);
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveApprovedReview(projectId, report.id());

        SqlPackage sqlPackage = service.exportSql(projectId);

        assertThat(service.preview(projectId).status()).isEqualTo("READY");
        assertThat(sqlPackage.fileName()).isEqualTo("001_schema_baseline.sql");
        assertThat(sqlPackage.content()).contains("create table \"users\"");
    }

    @Test
    void blocksStaleApprovalWhenLatestReportIsUnapproved() {
        UUID projectId = UUID.randomUUID();
        saveBaseline(projectId);
        StageReport oldReport = saveReport(projectId, ReportStatus.CURRENT, Instant.now().minusSeconds(10));
        saveApprovedReview(projectId, oldReport.id());
        saveReport(projectId, ReportStatus.CURRENT, Instant.now());

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Approved review");
    }

    @Test
    void blocksExportWhenApprovedReportBecomesStale() {
        UUID projectId = UUID.randomUUID();
        saveBaseline(projectId);
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveApprovedReview(projectId, report.id());
        reportRepository.markProjectReportsStale(projectId);

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Current precheck report");
    }

    @Test
    void blocksExportWhenLatestReviewRejectsApprovedReport() {
        UUID projectId = UUID.randomUUID();
        saveBaseline(projectId);
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveReview(projectId, report.id(), ReviewDecision.APPROVED, Instant.now().minusSeconds(10));
        saveReview(projectId, report.id(), ReviewDecision.REJECTED, Instant.now());

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Approved review");
    }

    @Test
    void blocksManualRequiredBaselineFromFormalExport() {
        UUID projectId = UUID.randomUUID();
        saveManualRequiredBaseline(projectId);
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveApprovedReview(projectId, report.id());

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Manual-required SQL");
    }

    @Test
    void blocksDraftBaselineFromFormalExport() {
        UUID projectId = UUID.randomUUID();
        saveDraftBaseline(projectId);
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveApprovedReview(projectId, report.id());

        assertThatThrownBy(() -> service.preview(projectId))
                .hasMessageContaining("Draft SQL");
    }

    @Test
    void exportsFrozenBaselinesInDependencyOrder() {
        UUID projectId = UUID.randomUUID();
        UUID tableObjectId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID viewObjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        saveBaseline(projectId, viewObjectId, "create view v_users as select id from users");
        saveBaseline(projectId, tableObjectId, "create table \"users\" (\"id\" numeric)");
        dependencyRepository.replaceProjectDependencies(projectId, java.util.List.of(new ObjectDependency(
                UUID.randomUUID(),
                projectId,
                projectId,
                viewObjectId,
                tableObjectId,
                DependencyType.VIEW_REFERENCES_TABLE,
                "users")));
        StageReport report = saveReport(projectId, ReportStatus.CURRENT, Instant.now());
        saveApprovedReview(projectId, report.id());

        String content = service.exportSql(projectId).content();

        assertThat(content.indexOf("create table \"users\"")).isLessThan(content.indexOf("create view v_users"));
    }

    private void saveBaseline(UUID projectId) {
        saveBaseline(projectId, UUID.randomUUID(), "create table \"users\" (\"id\" numeric)");
    }

    private void saveBaseline(UUID projectId, UUID objectId, String targetSql) {
        versionRepository.save(new SqlVersion(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                UUID.randomUUID(),
                null,
                1,
                SqlVersionSource.AUTO_CONVERSION,
                SqlVersionStatus.BASELINE,
                "create table users (id number)",
                targetSql,
                Instant.now()));
    }

    private void saveManualRequiredBaseline(UUID projectId) {
        versionRepository.save(new SqlVersion(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                1,
                SqlVersionSource.AUTO_CONVERSION,
                SqlVersionStatus.BASELINE,
                "create or replace package pkg as end;",
                "-- MANUAL_REQUIRED: Only conservative DDL conversion is available\ncreate or replace package pkg as end;",
                Instant.now()));
    }

    private void saveDraftBaseline(UUID projectId) {
        versionRepository.save(new SqlVersion(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                1,
                SqlVersionSource.AUTO_CONVERSION,
                SqlVersionStatus.BASELINE,
                "create view v_users as select nvl(name, '') from users;",
                "-- DRAFT: Review view before execution\ncreate view v_users as select nvl(name, '') from users;",
                Instant.now()));
    }

    private StageReport saveReport(UUID projectId, ReportStatus status, Instant createdAt) {
        StageReport stageReport = new StageReport(
                UUID.randomUUID(),
                projectId,
                StageReportType.PRECHECK,
                status,
                "Precheck Report",
                "{}",
                "<html></html>",
                createdAt);
        reportRepository.save(stageReport, new PrecheckReport(
                UUID.randomUUID(),
                projectId,
                stageReport.id(),
                status,
                1,
                0,
                0,
                0,
                0,
                1,
                1));
        return stageReport;
    }

    private void saveApprovedReview(UUID projectId, UUID reportId) {
        saveReview(projectId, reportId, ReviewDecision.APPROVED, Instant.now());
    }

    private void saveReview(UUID projectId, UUID reportId, ReviewDecision decision, Instant createdAt) {
        reviewRepository.save(new ReviewRecord(
                UUID.randomUUID(),
                projectId,
                reportId,
                decision,
                "dba",
                "ok",
                createdAt));
    }
}
