package org.sainm.schemapilot.skill;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;

import static org.assertj.core.api.Assertions.assertThat;

class SkillExecutorTest {
    private final SkillRegistry registry = new SkillRegistry();
    private final SkillExecutor executor = new SkillExecutor(registry, new ManualSqlAnalysisService(
            new SqlStatementSplitter(),
            new SqlObjectClassifier(),
            new OracleToPostgresConverter(),
            new SqlRiskDetector(),
            new RiskScoringService()
    ));

    @Test
    void exposesVersionedBuiltInSkillsWithAllowedTools() {
        assertThat(registry.definitions())
                .extracting(SkillDefinition::id)
                .contains("oracle-table-ddl", "oracle-view-sql", "oracle-trigger-to-postgres");
        assertThat(registry.get("oracle-trigger-to-postgres").allowedTools())
                .containsExactly("sql.analyze", "knowledge.search");
        assertThat(registry.get("oracle-trigger-to-postgres").requiresReview()).isTrue();
    }

    @Test
    void validatesTableSkillOutputAgainstDeclaredSchema() {
        var run = executor.execute("oracle-table-ddl", """
                CREATE TABLE users (
                  id NUMBER(19) PRIMARY KEY,
                  name VARCHAR2(100)
                );
                """);

        assertThat(run.skillVersion()).isEqualTo("1.0.0");
        assertThat(run.valid()).isTrue();
        assertThat(run.requiresReview()).isFalse();
        assertThat(run.output())
                .containsEntry("objectType", "TABLE")
                .containsKey("targetSql")
                .containsKey("risks");
    }

    @Test
    void triggerSkillRequiresHumanReview() {
        var run = executor.execute("oracle-trigger-to-postgres", """
                CREATE OR REPLACE TRIGGER trg_users_bi
                BEFORE INSERT ON users
                FOR EACH ROW
                BEGIN
                  :NEW.created_at := SYSDATE;
                END;
                /
                """);

        assertThat(run.valid()).isTrue();
        assertThat(run.requiresReview()).isTrue();
        assertThat(run.output())
                .containsEntry("objectType", "TRIGGER")
                .containsEntry("reviewRequired", true);
    }
}
