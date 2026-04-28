package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.model.RiskLevel;

public record SqlIssueItem(
        int statementIndex,
        String objectName,
        String riskType,
        RiskLevel level,
        String message,
        String suggestion
) {
}
