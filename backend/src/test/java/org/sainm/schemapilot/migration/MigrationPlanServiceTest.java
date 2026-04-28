package org.sainm.schemapilot.migration;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.sainm.schemapilot.datasource.DataSourceKind;
import org.sainm.schemapilot.datasource.InMemoryDataSourceConfigRepository;
import org.sainm.schemapilot.datasource.PasswordCipher;
import org.sainm.schemapilot.datasource.SaveDataSourceConfigRequest;
import org.sainm.schemapilot.model.ReportStatus;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;
import org.sainm.schemapilot.workbench.InMemoryWorkbenchRepository;
import org.sainm.schemapilot.workbench.ReviewRequest;
import org.sainm.schemapilot.workbench.WorkbenchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationPlanServiceTest {
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
    private final DataSourceConfigService dataSourceService = new DataSourceConfigService(
            new InMemoryDataSourceConfigRepository(),
            new PasswordCipher("test-secret"),
            new SensitiveValueRedactor()
    );
    private final MigrationPlanService service = new MigrationPlanService(
            workbenchService,
            dataSourceService,
            new InMemoryMigrationPlanRepository()
    );

    @Test
    void createsPlanOnlyFromApprovedFrozenBaseline() {
        var target = dataSourceService.create(new SaveDataSourceConfigRequest(
                "pg-target",
                DataSourceKind.POSTGRESQL,
                "jdbc:postgresql://localhost:6543/schemapilot",
                "schemapilot",
                "secret"
        ));
        var snapshot = workbenchService.saveManualSql("""
                CREATE TABLE users (id NUMBER);
                CREATE INDEX idx_users_id ON users(id);
                """);

        assertThatThrownBy(() -> service.create(new CreateMigrationPlanRequest(snapshot.id(), target.id())))
                .hasMessageContaining("approved");

        var submitted = workbenchService.submitReview(snapshot.id(), new ReviewRequest("alice", "ready"));
        var approved = workbenchService.approveReview(submitted.id(), new ReviewRequest("bob", "approved"));

        var plan = service.create(new CreateMigrationPlanRequest(approved.id(), target.id()));

        assertThat(approved.reportStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(plan.status()).isEqualTo(MigrationPlanStatus.READY);
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().getFirst().objectName()).isEqualTo("users");
        assertThat(plan.steps().getLast().objectName()).isEqualTo("idx_users_id");
    }

    @Test
    void executionFailureMarksStepAndCreatesWorkItem() {
        var target = dataSourceService.create(new SaveDataSourceConfigRequest(
                "pg-target",
                DataSourceKind.POSTGRESQL,
                "jdbc:postgresql://localhost:6543/schemapilot",
                "schemapilot",
                "secret"
        ));
        var snapshot = workbenchService.saveManualSql("CREATE TABLE users (id NUMBER);");
        var submitted = workbenchService.submitReview(snapshot.id(), new ReviewRequest("alice", "ready"));
        var approved = workbenchService.approveReview(submitted.id(), new ReviewRequest("bob", "approved"));
        var plan = service.create(new CreateMigrationPlanRequest(approved.id(), target.id()));

        var executed = service.execute(plan.id());

        assertThat(executed.status()).isEqualTo(MigrationPlanStatus.FAILED);
        assertThat(executed.steps().getFirst().status()).isEqualTo(MigrationPlanStepStatus.FAILED);
        assertThat(executed.steps().getFirst().workItem()).startsWith("DDL_WORK_ITEM");
        assertThat(executed.executionLog()).anyMatch(line -> line.contains("failed"));
    }
}
