package org.sainm.schemapilot.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.convert.ConversionLevel;
import org.sainm.schemapilot.convert.ConversionResult;
import org.sainm.schemapilot.convert.SqlVersion;
import org.sainm.schemapilot.convert.SqlVersionSource;
import org.sainm.schemapilot.convert.SqlVersionStatus;
import org.sainm.schemapilot.risk.ObjectRiskIssue;
import org.sainm.schemapilot.risk.RiskLevel;
import org.sainm.schemapilot.rule.RuleHit;

class ReportServiceTest {

    private final InMemoryReportRepository repository = new InMemoryReportRepository();
    private final ReportService service = new ReportService(repository);

    @Test
    void generatesBlockedPrecheckSnapshotWhenBlockerExists() {
        UUID projectId = UUID.randomUUID();
        ObjectRiskIssue blocker = new ObjectRiskIssue(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AUTONOMOUS_TRANSACTION_UNSUPPORTED",
                RiskLevel.BLOCKER,
                "No direct PostgreSQL equivalent",
                "PRAGMA AUTONOMOUS_TRANSACTION",
                new RuleHit("AUTONOMOUS_TRANSACTION_UNSUPPORTED", "Autonomous transaction", "PRAGMA", "Manual redesign"));

        GeneratedReport report = service.generatePrecheckReport(projectId, List.of(), List.of(), List.of(blocker), List.of(), List.of());

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.BLOCKED);
        assertThat(report.stageReport().jsonContent()).contains("\"blockerCount\": 1");
        assertThat(repository.findStageReports(projectId)).hasSize(1);
    }

    @Test
    void generatesBlockedPrecheckSnapshotWhenParseIssueExists() {
        UUID projectId = UUID.randomUUID();
        org.sainm.schemapilot.model.ParseIssue parseIssue = new org.sainm.schemapilot.model.ParseIssue(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UNSUPPORTED_STATEMENT",
                "Statement is not recognized",
                new org.sainm.schemapilot.model.SourceLocation("mixed.sql", 2, 2, 40, 76),
                "grant select on app_user to app_role");

        GeneratedReport report = service.generatePrecheckReport(projectId, List.of(), List.of(parseIssue), List.of(), List.of(), List.of());

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.BLOCKED);
        assertThat(report.precheckReport().parseIssueCount()).isEqualTo(1);
        assertThat(report.stageReport().jsonContent()).contains("\"parseIssueCount\": 1");
    }

    @Test
    void generatesBlockedPrecheckSnapshotWhenManualConversionExists() {
        UUID projectId = UUID.randomUUID();
        ConversionResult manualRequired = new ConversionResult(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "create or replace package pkg as end;",
                "-- MANUAL_REQUIRED: package conversion requires redesign",
                ConversionLevel.MANUAL_REQUIRED,
                List.of(),
                List.of("Manual conversion required"));

        GeneratedReport report = service.generatePrecheckReport(projectId, List.of(), List.of(), List.of(), List.of(manualRequired), List.of());

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.BLOCKED);
        assertThat(report.precheckReport().conversionCount()).isEqualTo(1);
    }

    @Test
    void generatesCurrentPrecheckSnapshotWhenManualConversionHasEditedSql() {
        UUID projectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        UUID conversionResultId = UUID.randomUUID();
        ConversionResult manualRequired = new ConversionResult(
                conversionResultId,
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                "create or replace package pkg as end;",
                "-- MANUAL_REQUIRED: package conversion requires redesign",
                ConversionLevel.MANUAL_REQUIRED,
                List.of(),
                List.of("Manual conversion required"));
        SqlVersion editedVersion = new SqlVersion(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                conversionResultId,
                null,
                2,
                SqlVersionSource.USER_EDIT,
                SqlVersionStatus.EDITED,
                manualRequired.sourceSql(),
                "create function pkg_run() returns void language plpgsql as $$ begin end $$;",
                Instant.now());

        GeneratedReport report = service.generatePrecheckReport(
                projectId,
                List.of(),
                List.of(),
                List.of(),
                List.of(manualRequired),
                List.of(editedVersion));

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.CURRENT);
    }

    @Test
    void generatesBlockedPrecheckSnapshotWhenDraftConversionIsUnedited() {
        UUID projectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        UUID conversionResultId = UUID.randomUUID();
        ConversionResult draft = new ConversionResult(
                conversionResultId,
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                "create view v_users as select nvl(name, '') from users;",
                "-- DRAFT: review view before execution\ncreate view v_users as select nvl(name, '') from users;",
                ConversionLevel.DRAFT,
                List.of(),
                List.of("Draft needs review"));

        GeneratedReport report = service.generatePrecheckReport(
                projectId,
                List.of(),
                List.of(),
                List.of(),
                List.of(draft),
                List.of());

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.BLOCKED);
    }

    @Test
    void generatesCurrentPrecheckSnapshotWhenDraftConversionHasEditedSql() {
        UUID projectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        UUID conversionResultId = UUID.randomUUID();
        ConversionResult draft = new ConversionResult(
                conversionResultId,
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                "create view v_users as select nvl(name, '') from users;",
                "-- DRAFT: review view before execution\ncreate view v_users as select nvl(name, '') from users;",
                ConversionLevel.DRAFT,
                List.of(),
                List.of("Draft needs review"));
        SqlVersion editedVersion = new SqlVersion(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectId,
                conversionResultId,
                null,
                2,
                SqlVersionSource.USER_EDIT,
                SqlVersionStatus.EDITED,
                draft.sourceSql(),
                "create view v_users as select coalesce(name, '') from users;",
                Instant.now());

        GeneratedReport report = service.generatePrecheckReport(
                projectId,
                List.of(),
                List.of(),
                List.of(),
                List.of(draft),
                List.of(editedVersion));

        assertThat(report.stageReport().status()).isEqualTo(ReportStatus.CURRENT);
    }

    @Test
    void staleReportExportsShowStaleStatus() {
        UUID projectId = UUID.randomUUID();
        GeneratedReport report = service.generatePrecheckReport(projectId, List.of(), List.of(), List.of(), List.of(), List.of());

        repository.markProjectReportsStale(projectId);

        StageReport staleReport = repository.findStageReport(report.stageReport().id()).orElseThrow();
        assertThat(staleReport.status()).isEqualTo(ReportStatus.STALE);
        assertThat(staleReport.jsonContent()).contains("\"status\": \"STALE\"");
        assertThat(staleReport.htmlContent()).contains("<dt>Status</dt><dd>STALE</dd>");
    }
}
