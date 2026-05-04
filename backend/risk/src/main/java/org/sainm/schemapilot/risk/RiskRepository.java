package org.sainm.schemapilot.risk;

import java.util.List;
import java.util.UUID;

public interface RiskRepository {

    void replaceInputSourceRisks(UUID inputSourceId, List<ObjectRiskIssue> risks);

    List<ObjectRiskIssue> findRisks(UUID projectId);
}
