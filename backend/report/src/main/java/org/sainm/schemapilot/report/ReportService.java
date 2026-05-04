package org.sainm.schemapilot.report;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.sainm.schemapilot.convert.ConversionLevel;
import org.sainm.schemapilot.convert.ConversionResult;
import org.sainm.schemapilot.convert.SqlVersion;
import org.sainm.schemapilot.convert.SqlVersionStatus;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.ParseIssue;
import org.sainm.schemapilot.risk.ObjectRiskIssue;
import org.sainm.schemapilot.risk.RiskLevel;

public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public GeneratedReport generatePrecheckReport(
            UUID projectId,
            List<DbObject> objects,
            List<ParseIssue> parseIssues,
            List<ObjectRiskIssue> risks,
            List<ConversionResult> conversions,
            List<SqlVersion> sqlVersions) {
        int blockerCount = countRisk(risks, RiskLevel.BLOCKER);
        int highRiskCount = countRisk(risks, RiskLevel.HIGH);
        ReportStatus status = blockerCount > 0 || !parseIssues.isEmpty() || hasUnresolvedNonExportableConversion(conversions, sqlVersions)
                ? ReportStatus.BLOCKED
                : ReportStatus.CURRENT;
        UUID stageReportId = UUID.randomUUID();
        UUID precheckReportId = UUID.randomUUID();
        String json = jsonContent(projectId, status, objects.size(), parseIssues.size(), risks.size(), blockerCount, highRiskCount, conversions.size(), sqlVersions.size());
        String html = htmlContent(projectId, status, objects.size(), parseIssues.size(), risks.size(), blockerCount, highRiskCount, conversions.size(), sqlVersions.size());
        StageReport stageReport = new StageReport(
                stageReportId,
                projectId,
                StageReportType.PRECHECK,
                status,
                "Precheck Report",
                json,
                html,
                Instant.now());
        PrecheckReport precheckReport = new PrecheckReport(
                precheckReportId,
                projectId,
                stageReportId,
                status,
                objects.size(),
                parseIssues.size(),
                risks.size(),
                blockerCount,
                highRiskCount,
                conversions.size(),
                sqlVersions.size());
        reportRepository.save(stageReport, precheckReport);
        return new GeneratedReport(stageReport, precheckReport);
    }

    private int countRisk(List<ObjectRiskIssue> risks, RiskLevel level) {
        return (int) risks.stream().filter(risk -> risk.level() == level).count();
    }

    private boolean hasUnresolvedNonExportableConversion(List<ConversionResult> conversions, List<SqlVersion> sqlVersions) {
        return conversions.stream()
                .filter(conversion -> conversion.level() == ConversionLevel.MANUAL_REQUIRED || conversion.level() == ConversionLevel.DRAFT)
                .anyMatch(conversion -> latestUsableVersion(conversion.objectId(), sqlVersions)
                        .map(version -> isNonExportableSql(version.targetSql()))
                        .orElse(true));
    }

    private Optional<SqlVersion> latestUsableVersion(UUID objectId, List<SqlVersion> sqlVersions) {
        return sqlVersions.stream()
                .filter(version -> version.objectId().equals(objectId))
                .filter(version -> version.status() != SqlVersionStatus.STALE)
                .max(Comparator.comparingInt(SqlVersion::versionNumber));
    }

    private boolean isNonExportableSql(String sql) {
        if (sql == null) {
            return true;
        }
        String stripped = sql.stripLeading();
        return stripped.startsWith("-- MANUAL_REQUIRED") || stripped.startsWith("-- DRAFT");
    }

    private String jsonContent(
            UUID projectId,
            ReportStatus status,
            int objectCount,
            int parseIssueCount,
            int riskCount,
            int blockerCount,
            int highRiskCount,
            int conversionCount,
            int sqlVersionCount) {
        return """
                {
                  "projectId": "%s",
                  "type": "PRECHECK",
                  "status": "%s",
                  "objectCount": %d,
                  "parseIssueCount": %d,
                  "riskCount": %d,
                  "blockerCount": %d,
                  "highRiskCount": %d,
                  "conversionCount": %d,
                  "sqlVersionCount": %d
                }
                """.formatted(projectId, status, objectCount, parseIssueCount, riskCount, blockerCount, highRiskCount, conversionCount, sqlVersionCount);
    }

    private String htmlContent(
            UUID projectId,
            ReportStatus status,
            int objectCount,
            int parseIssueCount,
            int riskCount,
            int blockerCount,
            int highRiskCount,
            int conversionCount,
            int sqlVersionCount) {
        return """
                <!doctype html>
                <html>
                <head><meta charset="utf-8"><title>SchemaPilot Precheck Report</title></head>
                <body>
                  <h1>SchemaPilot Precheck Report</h1>
                  <dl>
                    <dt>Project</dt><dd>%s</dd>
                    <dt>Status</dt><dd>%s</dd>
                    <dt>Objects</dt><dd>%d</dd>
                    <dt>Parse Issues</dt><dd>%d</dd>
                    <dt>Risks</dt><dd>%d</dd>
                    <dt>Blockers</dt><dd>%d</dd>
                    <dt>High Risks</dt><dd>%d</dd>
                    <dt>Conversions</dt><dd>%d</dd>
                    <dt>SQL Versions</dt><dd>%d</dd>
                  </dl>
                </body>
                </html>
                """.formatted(projectId, status, objectCount, parseIssueCount, riskCount, blockerCount, highRiskCount, conversionCount, sqlVersionCount);
    }
}
