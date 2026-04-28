package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ConversionLevel;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;

import java.util.List;

public record AnalyzedStatement(
        int index,
        ObjectType objectType,
        String objectName,
        String originalSql,
        String postgresSql,
        ConversionLevel conversionLevel,
        RiskLevel riskLevel,
        List<DetectedRisk> risks,
        List<ParseIssue> parseIssues,
        String aiSuggestion
) {
}
