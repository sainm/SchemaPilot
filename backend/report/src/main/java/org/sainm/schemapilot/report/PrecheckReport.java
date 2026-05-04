package org.sainm.schemapilot.report;

import java.util.UUID;

public record PrecheckReport(
        UUID id,
        UUID projectId,
        UUID stageReportId,
        ReportStatus status,
        int objectCount,
        int parseIssueCount,
        int riskCount,
        int blockerCount,
        int highRiskCount,
        int conversionCount,
        int sqlVersionCount) {
}
