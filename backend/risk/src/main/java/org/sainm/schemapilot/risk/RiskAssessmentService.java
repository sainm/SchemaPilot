package org.sainm.schemapilot.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.rule.RuleHit;

public class RiskAssessmentService {

    private final SqlRiskDetector detector;
    private final RiskRepository riskRepository;

    public RiskAssessmentService(SqlRiskDetector detector, RiskRepository riskRepository) {
        this.detector = detector;
        this.riskRepository = riskRepository;
    }

    public List<ObjectRiskIssue> assessObjects(List<DbObject> objects) {
        List<ObjectRiskIssue> risks = new ArrayList<>();
        for (DbObject object : objects) {
            for (RiskIssue detected : detector.detect(object.originalSql())) {
                risks.add(new ObjectRiskIssue(
                        UUID.randomUUID(),
                        object.projectId(),
                        object.sourceProjectId(),
                        object.inputSourceId(),
                        object.id(),
                        detected.code(),
                        detected.level(),
                        detected.message(),
                        detected.evidence(),
                        new RuleHit(
                                detected.code(),
                                detected.message(),
                                detected.evidence(),
                                detected.message())));
            }
        }
        if (!objects.isEmpty()) {
            riskRepository.replaceInputSourceRisks(objects.getFirst().inputSourceId(), risks);
        }
        return List.copyOf(risks);
    }
}
