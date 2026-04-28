package org.sainm.schemapilot.export;

import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;
import org.sainm.schemapilot.workbench.InMemoryWorkbenchRepository;
import org.sainm.schemapilot.workbench.ReviewRequest;
import org.sainm.schemapilot.workbench.WorkbenchService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlPackageExportServiceTest {
    private final WorkbenchService workbenchService = new WorkbenchService(
            new ManualSqlAnalysisService(
                    new SqlStatementSplitter(),
                    new SqlObjectClassifier(),
                    new OracleToPostgresConverter(),
                    new SqlRiskDetector(),
                    new RiskScoringService()
            ),
            new InMemoryWorkbenchRepository()
    );
    private final SqlPackageExportService exportService = new SqlPackageExportService(workbenchService);

    @Test
    void blocksExportBeforeApproval() {
        var snapshot = workbenchService.saveManualSql("CREATE TABLE users (id NUMBER);");

        assertThatThrownBy(() -> exportService.export(snapshot.id()))
                .hasMessageContaining("approved");
    }

    @Test
    void exportsOnlyFrozenBaselineSqlInObjectOrder() {
        var snapshot = workbenchService.saveManualSql("""
                CREATE INDEX idx_users_name ON users(name);
                CREATE TABLE users (id NUMBER, name VARCHAR2(100));
                CREATE SEQUENCE users_seq START WITH 1;
                """);
        var submitted = workbenchService.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = workbenchService.approveReview(submitted.id(), new ReviewRequest("bob", "approve"));

        var exported = exportService.export(approved.id());

        assertThat(exported.fileName()).endsWith(".sql");
        assertThat(exported.sqlVersionIds()).hasSize(3);
        assertThat(exported.content()).contains("CREATE TABLE users").contains("CREATE INDEX idx_users_name");
        assertThat(exported.content().indexOf("CREATE SEQUENCE users_seq"))
                .isLessThan(exported.content().indexOf("CREATE TABLE users"));
        assertThat(exported.content().indexOf("CREATE TABLE users"))
                .isLessThan(exported.content().indexOf("CREATE INDEX idx_users_name"));
        assertThat(workbenchService.getSnapshot(approved.id()).auditEvents())
                .extracting(org.sainm.schemapilot.workbench.AuditEvent::action)
                .contains("SQL_PACKAGE_EXPORTED");
    }

    @Test
    void blocksExportWhenReportOrBaselineIsExpired() {
        var snapshot = workbenchService.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = workbenchService.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = workbenchService.approveReview(submitted.id(), new ReviewRequest("bob", "approve"));
        var expired = workbenchService.editTargetSql(approved.id(), new org.sainm.schemapilot.workbench.EditSqlVersionRequest(1, "CREATE TABLE users (id bigint);"));

        assertThatThrownBy(() -> exportService.export(expired.id()))
                .hasMessageContaining("approved");
    }
}
