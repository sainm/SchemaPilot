package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.model.ReportStatus;
import org.sainm.schemapilot.model.SqlBaselineStatus;
import org.sainm.schemapilot.sql.SqlAnalysisResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkbenchSnapshot(
        UUID id,
        Instant createdAt,
        String reportVersion,
        String originalSql,
        SqlAnalysisResponse analysis,
        ReportStatus reportStatus,
        SqlBaselineStatus baselineStatus,
        boolean baselineFrozen,
        List<SavedSqlVersion> sqlVersions,
        List<SavedAiSuggestion> aiSuggestions,
        List<ReviewRecord> reviewRecords,
        List<AuditEvent> auditEvents
) {
}
