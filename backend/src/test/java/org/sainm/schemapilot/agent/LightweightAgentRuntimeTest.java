package org.sainm.schemapilot.agent;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.AgentStatus;
import org.sainm.schemapilot.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration"
})
class LightweightAgentRuntimeTest {
    @MockitoBean
    ProjectRepository projectRepository;

    @Autowired
    AgentRuntime runtime;

    @Test
    void assessmentAgentRecordsAuditedToolStepsAndWaitsForHumanDecision() {
        var run = runtime.run(new AgentRunRequest(
                AgentType.ASSESSMENT,
                "precheck a small schema",
                """
                        CREATE TABLE users (
                          id NUMBER(19) PRIMARY KEY,
                          created_at DATE DEFAULT SYSDATE
                        );
                        """,
                null
        ));

        assertThat(run.status()).isEqualTo(AgentStatus.AWAITING_USER_DECISION);
        assertThat(run.steps()).extracting(AgentStep::toolName).contains("sql.analyze", "knowledge.search");
        assertThat(run.steps()).extracting(AgentStep::status).contains(AgentStatus.AUDITED);
        assertThat(run.result())
                .containsEntry("statementCount", 1)
                .containsKey("compatibilityScore")
                .containsKey("recommendedGate");
    }

    @Test
    void conversionAgentUsesSkillButStillRequiresReviewGate() {
        var run = runtime.run(new AgentRunRequest(
                AgentType.CONVERSION,
                "draft table conversion",
                "CREATE TABLE accounts (id NUMBER(19) PRIMARY KEY, email VARCHAR2(200));",
                null
        ));

        assertThat(run.status()).isEqualTo(AgentStatus.AWAITING_USER_DECISION);
        assertThat(run.steps()).extracting(AgentStep::toolName).contains("sql.analyze", "skill.run");
        assertThat(run.result())
                .containsEntry("skillId", "oracle-table-ddl")
                .containsEntry("requiresReview", true);
    }

    @Test
    void failedAgentRunKeepsFailureStepForAudit() {
        var run = runtime.run(new AgentRunRequest(
                AgentType.ERROR_DIAGNOSIS,
                "diagnose error",
                null,
                null
        ));

        assertThat(run.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(run.steps()).extracting(AgentStep::status).contains(AgentStatus.FAILED);
        assertThat(run.result()).containsKey("error");
    }
}
