package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.model.ReportStatus;
import org.sainm.schemapilot.model.SqlBaselineStatus;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbenchServiceTest {
    private final WorkbenchService service = new WorkbenchService(
            new ManualSqlAnalysisService(
                    new SqlStatementSplitter(),
                    new SqlObjectClassifier(),
                    new OracleToPostgresConverter(),
                    new SqlRiskDetector(),
                    new RiskScoringService()
            ),
            new InMemoryWorkbenchRepository()
    );

    @Test
    void savesManualSqlWithGeneratedConversionVersions() {
        var snapshot = service.saveManualSql("""
                CREATE TABLE users (
                  id NUMBER(19) PRIMARY KEY,
                  name VARCHAR2(100)
                );
                """);

        assertThat(snapshot.originalSql()).contains("CREATE TABLE users");
        assertThat(snapshot.reportVersion()).startsWith("report-");
        assertThat(snapshot.analysis().statementCount()).isEqualTo(1);
        assertThat(snapshot.reportStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(snapshot.baselineStatus()).isEqualTo(SqlBaselineStatus.GENERATED);
        assertThat(snapshot.baselineFrozen()).isFalse();
        assertThat(snapshot.sqlVersions()).hasSize(1);
        assertThat(snapshot.sqlVersions().getFirst().source()).isEqualTo(SqlVersionSource.RULE_GENERATED);
        assertThat(snapshot.sqlVersions().getFirst().sql()).contains("name varchar(100)");
        assertThat(snapshot.auditEvents())
                .extracting(AuditEvent::action)
                .contains("SNAPSHOT_CREATED");
    }

    @Test
    void savesAndReviewsAiSuggestionWithoutAutomaticBaselineOrApproval() {
        var snapshot = service.saveManualSql("""
                CREATE TABLE users (id NUMBER, name VARCHAR2(100));
                """);

        var withSuggestion = service.saveAiSuggestion(new SaveAiSuggestionRequest(
                snapshot.id(),
                1,
                "mock",
                "schemapilot-rule-backed",
                "mock-v1",
                "Use bigint for id after checking source data.",
                List.of("riskType=NUMBER_PRECISION"),
                List.of("type.number")
        ));
        var suggestion = withSuggestion.aiSuggestions().getFirst();

        assertThat(suggestion.inputHash()).hasSize(64);
        assertThat(suggestion.citedChunkKeys()).contains("type.number");
        assertThat(suggestion.status()).isEqualTo(AiSuggestionStatus.GENERATED);
        assertThat(withSuggestion.reportStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(withSuggestion.baselineFrozen()).isFalse();

        var accepted = service.acceptAiSuggestion(suggestion.id());
        assertThat(accepted.aiSuggestions().getFirst().status()).isEqualTo(AiSuggestionStatus.ACCEPTED);
        assertThat(accepted.baselineFrozen()).isFalse();
        assertThat(accepted.reportStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(accepted.auditEvents())
                .extracting(AuditEvent::action)
                .contains("AI_SUGGESTION_SAVED", "AI_SUGGESTION_ACCEPTED");

        var applied = service.applyEditedAiSuggestion(suggestion.id(), "CREATE TABLE users (id bigint PRIMARY KEY, name varchar(100));");
        assertThat(applied.aiSuggestions().getFirst().status()).isEqualTo(AiSuggestionStatus.APPLIED);
        assertThat(applied.sqlVersions())
                .extracting(SavedSqlVersion::source)
                .contains(SqlVersionSource.RULE_GENERATED, SqlVersionSource.AI_SUGGESTION);
        assertThat(applied.reportStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(applied.baselineFrozen()).isFalse();
        assertThat(applied.auditEvents())
                .extracting(AuditEvent::action)
                .contains("AI_SUGGESTION_APPLIED");
    }

    @Test
    void canIgnoreAiSuggestion() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var withSuggestion = service.saveAiSuggestion(new SaveAiSuggestionRequest(
                snapshot.id(),
                1,
                "mock",
                "schemapilot-rule-backed",
                null,
                "Keep numeric until data profile confirms range.",
                List.of(),
                List.of()
        ));

        var ignored = service.ignoreAiSuggestion(withSuggestion.aiSuggestions().getFirst().id());

        assertThat(ignored.aiSuggestions().getFirst().status()).isEqualTo(AiSuggestionStatus.IGNORED);
        assertThat(ignored.sqlVersions()).hasSize(1);
        assertThat(ignored.baselineFrozen()).isFalse();
    }

    @Test
    void reviewApprovalFreezesBaselineAndBindsVersions() {
        var snapshot = service.saveManualSql("""
                CREATE TABLE users (id NUMBER, name VARCHAR2(100));
                CREATE INDEX idx_users_name ON users(name);
                """);

        var submitted = service.submitReview(snapshot.id(), new ReviewRequest("alice", "ready"));
        var approved = service.approveReview(submitted.id(), new ReviewRequest("bob", "approved"));

        assertThat(submitted.reportStatus()).isEqualTo(ReportStatus.READY_FOR_REVIEW);
        assertThat(approved.reportStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(approved.baselineStatus()).isEqualTo(SqlBaselineStatus.BASELINED);
        assertThat(approved.baselineFrozen()).isTrue();
        assertThat(approved.sqlVersions())
                .extracting(SavedSqlVersion::status)
                .containsOnly(SqlBaselineStatus.BASELINED);
        assertThat(approved.reviewRecords())
                .extracting(ReviewRecord::decision)
                .contains(ReviewDecision.SUBMITTED, ReviewDecision.APPROVED);
        assertThat(approved.reviewRecords().getLast().boundSqlVersionIds()).hasSize(2);
    }

    @Test
    void reviewCanRejectOrRequestChangesWithoutFreezingBaseline() {
        var rejected = service.submitReview(
                service.saveManualSql("CREATE TABLE users (id NUMBER);").id(),
                new ReviewRequest("alice", "submit")
        );
        rejected = service.rejectReview(rejected.id(), new ReviewRequest("bob", "no"));

        var changes = service.submitReview(
                service.saveManualSql("CREATE TABLE accounts (id NUMBER);").id(),
                new ReviewRequest("alice", "submit")
        );
        changes = service.requestChanges(changes.id(), new ReviewRequest("bob", "fix id type"));

        assertThat(rejected.reportStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(rejected.baselineFrozen()).isFalse();
        assertThat(changes.reportStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(changes.reviewRecords().getLast().decision()).isEqualTo(ReviewDecision.CHANGES_REQUESTED);
    }

    @Test
    void expiredReportCannotPassReviewGate() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var expired = service.editTargetSql(snapshot.id(), new EditSqlVersionRequest(1, "CREATE TABLE users (id bigint);"));

        assertThat(expired.reportStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(expired.baselineStatus()).isEqualTo(SqlBaselineStatus.EDITED);
        assertThatThrownBy(() -> service.submitReview(expired.id(), new ReviewRequest("alice", "try")))
                .hasMessageContaining("Expired report");
    }

    @Test
    void conditionalApprovalAlsoFreezesBaseline() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = service.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = service.conditionallyApproveReview(submitted.id(), new ReviewRequest("bob", "condition: verify data profile"));

        assertThat(approved.reportStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(approved.baselineStatus()).isEqualTo(SqlBaselineStatus.BASELINED);
        assertThat(approved.reviewRecords().getLast().decision()).isEqualTo(ReviewDecision.CONDITIONALLY_APPROVED);
    }

    @Test
    void editingAfterApprovalExpiresOldReportAndBaseline() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = service.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = service.approveReview(submitted.id(), new ReviewRequest("bob", "approve"));

        var edited = service.editTargetSql(approved.id(), new EditSqlVersionRequest(1, "CREATE TABLE users (id bigint);"));

        assertThat(edited.reportStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(edited.baselineStatus()).isEqualTo(SqlBaselineStatus.EXPIRED);
        assertThat(edited.baselineFrozen()).isFalse();
        assertThat(edited.auditEvents())
                .extracting(AuditEvent::action)
                .contains("TARGET_SQL_EDITED");
    }

    @Test
    void replacingSourceSqlExpiresReportAndRegeneratesObjects() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = service.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = service.approveReview(submitted.id(), new ReviewRequest("bob", "approve"));

        var replaced = service.replaceSourceSql(approved.id(), """
                CREATE TABLE accounts (id NUMBER);
                CREATE INDEX idx_accounts_id ON accounts(id);
                """);

        assertThat(replaced.originalSql()).contains("CREATE TABLE accounts");
        assertThat(replaced.analysis().statementCount()).isEqualTo(2);
        assertThat(replaced.reportStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(replaced.baselineStatus()).isEqualTo(SqlBaselineStatus.EXPIRED);
        assertThat(replaced.baselineFrozen()).isFalse();
        assertThat(replaced.auditEvents())
                .extracting(AuditEvent::action)
                .contains("SOURCE_SQL_REPLACED");
    }

    @Test
    void ruleConfigChangeExpiresExistingSnapshots() {
        var snapshot = service.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = service.submitReview(snapshot.id(), new ReviewRequest("alice", "submit"));
        var approved = service.approveReview(submitted.id(), new ReviewRequest("bob", "approve"));

        var expired = service.expireAllForRuleConfigChange("type-rules@2").getFirst();

        assertThat(expired.id()).isEqualTo(approved.id());
        assertThat(expired.reportStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(expired.baselineStatus()).isEqualTo(SqlBaselineStatus.EXPIRED);
        assertThat(expired.auditEvents())
                .extracting(AuditEvent::action)
                .contains("RULE_CONFIG_CHANGED");
    }
}
