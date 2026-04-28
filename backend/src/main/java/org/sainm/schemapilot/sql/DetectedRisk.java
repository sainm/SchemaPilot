package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.RiskLevel;

public record DetectedRisk(
        String type,
        RiskLevel level,
        String message,
        String suggestion
) {
}
