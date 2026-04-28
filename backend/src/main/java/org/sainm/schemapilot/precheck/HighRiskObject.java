package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;

import java.util.List;

public record HighRiskObject(
        int statementIndex,
        ObjectType objectType,
        String objectName,
        RiskLevel riskLevel,
        List<String> riskTypes
) {
}
