package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;
import org.sainm.schemapilot.sql.SqlAnalysisResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PrecheckReportResponse(
        String reportVersion,
        Instant generatedAt,
        SqlAnalysisResponse analysis,
        Map<ObjectType, Long> objectTypeDistribution,
        Map<RiskLevel, Long> riskDistribution,
        List<HighRiskObject> highRiskObjects,
        List<TypeMappingItem> typeMappings,
        List<SqlIssueItem> issues,
        List<String> handlingRecommendations,
        List<String> migrationOrderDraft,
        String managementSummary,
        String developerSummary,
        Map<String, String> versionRefs
) {
}
