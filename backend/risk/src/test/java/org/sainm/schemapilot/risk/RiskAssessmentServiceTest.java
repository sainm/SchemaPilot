package org.sainm.schemapilot.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.model.ObjectStatus;
import org.sainm.schemapilot.model.SourceLocation;

class RiskAssessmentServiceTest {

    private final InMemoryRiskRepository repository = new InMemoryRiskRepository();
    private final RiskAssessmentService service = new RiskAssessmentService(new SqlRiskDetector(), repository);

    @Test
    void storesRiskWithObjectAndRuleEvidence() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "users",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 42),
                "create table users (id number(10,0) default sysdate)",
                List.of());

        List<ObjectRiskIssue> risks = service.assessObjects(List.of(object));

        assertThat(risks).extracting(ObjectRiskIssue::code)
                .contains("NUMBER_INTEGER_NARROWING_REVIEW", "SYSDATE_TIME_SEMANTICS");
        assertThat(repository.findRisks(projectId).getFirst().objectId()).isEqualTo(object.id());
        assertThat(repository.findRisks(projectId).getFirst().ruleHit()).isNotNull();
    }
}
