package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.model.ReportStatus;
import org.sainm.schemapilot.model.SqlBaselineStatus;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class WorkbenchService {
    private final ManualSqlAnalysisService analysisService;
    private final WorkbenchRepository repository;

    public WorkbenchService(ManualSqlAnalysisService analysisService, WorkbenchRepository repository) {
        this.analysisService = analysisService;
        this.repository = repository;
    }

    public WorkbenchSnapshot saveManualSql(String sql) {
        return saveSql(sql, "SNAPSHOT_CREATED", "Manual SQL was analyzed and saved with generated conversion versions.");
    }

    public WorkbenchSnapshot saveImportedSql(String sql, String fileName) {
        return saveSql(sql, "FILE_IMPORT_SNAPSHOT_CREATED", "SQL file import was analyzed and saved with generated conversion versions: " + fileName + ".");
    }

    private WorkbenchSnapshot saveSql(String sql, String auditAction, String auditMessage) {
        var analysis = analysisService.analyze(sql);
        var snapshotId = UUID.randomUUID();
        var now = Instant.now();
        var versions = analysis.statements().stream()
                .map(statement -> new SavedSqlVersion(
                        UUID.randomUUID(),
                        statement.index(),
                        SqlVersionSource.RULE_GENERATED,
                        SqlBaselineStatus.GENERATED,
                        statement.postgresSql(),
                        now
                ))
                .toList();

        return repository.save(new WorkbenchSnapshot(
                snapshotId,
                now,
                "report-" + snapshotId,
                sql,
                analysis,
                ReportStatus.DRAFT,
                SqlBaselineStatus.GENERATED,
                false,
                versions,
                List.of(),
                List.of(),
                List.of(AuditEvent.of(auditAction, auditMessage))
        ));
    }

    public WorkbenchSnapshot getSnapshot(UUID snapshotId) {
        return findSnapshot(snapshotId);
    }

    public WorkbenchSnapshot replaceSourceSql(UUID snapshotId, String sql) {
        var snapshot = findSnapshot(snapshotId);
        var analysis = analysisService.analyze(sql);
        var now = Instant.now();
        var versions = analysis.statements().stream()
                .map(statement -> new SavedSqlVersion(
                        UUID.randomUUID(),
                        statement.index(),
                        SqlVersionSource.RULE_GENERATED,
                        SqlBaselineStatus.GENERATED,
                        statement.postgresSql(),
                        now
                ))
                .toList();
        return repository.save(new WorkbenchSnapshot(
                snapshot.id(),
                snapshot.createdAt(),
                "report-" + snapshot.id() + "-expired-" + now.toEpochMilli(),
                sql,
                analysis,
                ReportStatus.EXPIRED,
                snapshot.baselineFrozen() ? SqlBaselineStatus.EXPIRED : SqlBaselineStatus.GENERATED,
                false,
                versions,
                snapshot.aiSuggestions(),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("SOURCE_SQL_REPLACED", "Source SQL changed; report and baseline were expired."))
        ));
    }

    public List<WorkbenchSnapshot> expireAllForRuleConfigChange(String ruleConfigVersion) {
        return repository.findAllSnapshots().stream()
                .map(snapshot -> updateSnapshot(
                        snapshot,
                        snapshot.sqlVersions(),
                        snapshot.aiSuggestions(),
                        snapshot.reviewRecords(),
                        append(snapshot.auditEvents(), AuditEvent.of("RULE_CONFIG_CHANGED", "Rule config " + ruleConfigVersion + " changed; report and baseline were expired.")),
                        ReportStatus.EXPIRED,
                        snapshot.baselineFrozen() ? SqlBaselineStatus.EXPIRED : snapshot.baselineStatus(),
                        false
                ))
                .toList();
    }

    public WorkbenchSnapshot saveAiSuggestion(SaveAiSuggestionRequest request) {
        var snapshot = findSnapshot(request.snapshotId());
        assertStatementExists(snapshot, request.statementIndex());
        var suggestion = new SavedAiSuggestion(
                UUID.randomUUID(),
                request.snapshotId(),
                request.statementIndex(),
                request.provider(),
                request.model(),
                request.promptVersion() == null || request.promptVersion().isBlank() ? "mock-v1" : request.promptVersion(),
                sha256(request.model() + "\n" + request.suggestion() + "\n" + String.join("\n", request.evidence()) + "\n" + String.join("\n", request.citedChunkKeys())),
                request.suggestion(),
                request.evidence(),
                request.citedChunkKeys(),
                AiSuggestionStatus.GENERATED,
                Instant.now(),
                Instant.now()
        );
        return updateSnapshot(
                snapshot,
                snapshot.sqlVersions(),
                append(snapshot.aiSuggestions(), suggestion),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("AI_SUGGESTION_SAVED", "AI suggestion saved for statement " + request.statementIndex() + ".")),
                snapshot.reportStatus(),
                snapshot.baselineStatus(),
                snapshot.baselineFrozen()
        );
    }

    public WorkbenchSnapshot acceptAiSuggestion(UUID suggestionId) {
        return updateSuggestionStatus(suggestionId, AiSuggestionStatus.ACCEPTED, "AI_SUGGESTION_ACCEPTED", "AI suggestion accepted for review.");
    }

    public WorkbenchSnapshot ignoreAiSuggestion(UUID suggestionId) {
        return updateSuggestionStatus(suggestionId, AiSuggestionStatus.IGNORED, "AI_SUGGESTION_IGNORED", "AI suggestion ignored.");
    }

    public WorkbenchSnapshot applyEditedAiSuggestion(UUID suggestionId, String targetSql) {
        var suggestion = findSuggestion(suggestionId);
        var snapshot = findSnapshot(suggestion.snapshotId());
        var updatedSuggestion = suggestion.withStatus(AiSuggestionStatus.APPLIED);
        var versions = append(snapshot.sqlVersions(), new SavedSqlVersion(
                UUID.randomUUID(),
                suggestion.statementIndex(),
                SqlVersionSource.AI_SUGGESTION,
                SqlBaselineStatus.EDITED,
                targetSql,
                Instant.now()
        ));
        return updateSnapshot(
                snapshot,
                versions,
                replaceSuggestion(snapshot.aiSuggestions(), updatedSuggestion),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("AI_SUGGESTION_APPLIED", "Edited AI suggestion created a new target SQL version and expired the report.")),
                ReportStatus.EXPIRED,
                snapshot.baselineFrozen() ? SqlBaselineStatus.EXPIRED : snapshot.baselineStatus(),
                false
        );
    }

    public WorkbenchSnapshot editTargetSql(UUID snapshotId, EditSqlVersionRequest request) {
        var snapshot = findSnapshot(snapshotId);
        assertStatementExists(snapshot, request.statementIndex());
        var versions = append(snapshot.sqlVersions(), new SavedSqlVersion(
                UUID.randomUUID(),
                request.statementIndex(),
                SqlVersionSource.MANUAL_EDIT,
                SqlBaselineStatus.EDITED,
                request.targetSql(),
                Instant.now()
        ));
        return updateSnapshot(
                snapshot,
                versions,
                snapshot.aiSuggestions(),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("TARGET_SQL_EDITED", "Manual edit created a new SQL version and expired report/baseline.")),
                ReportStatus.EXPIRED,
                snapshot.baselineFrozen() ? SqlBaselineStatus.EXPIRED : SqlBaselineStatus.EDITED,
                false
        );
    }

    public WorkbenchSnapshot restoreGeneratedVersion(UUID snapshotId, int statementIndex) {
        var snapshot = findSnapshot(snapshotId);
        var generated = snapshot.sqlVersions().stream()
                .filter(version -> version.statementIndex() == statementIndex)
                .filter(version -> version.source() == SqlVersionSource.RULE_GENERATED)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Generated SQL version not found for statement: " + statementIndex));
        var versions = append(snapshot.sqlVersions(), new SavedSqlVersion(
                UUID.randomUUID(),
                statementIndex,
                SqlVersionSource.RULE_GENERATED,
                SqlBaselineStatus.GENERATED,
                generated.sql(),
                Instant.now()
        ));
        return updateSnapshot(
                snapshot,
                versions,
                snapshot.aiSuggestions(),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("TARGET_SQL_RESTORED", "Generated SQL version restored for statement " + statementIndex + ".")),
                ReportStatus.EXPIRED,
                snapshot.baselineFrozen() ? SqlBaselineStatus.EXPIRED : SqlBaselineStatus.GENERATED,
                false
        );
    }

    public WorkbenchSnapshot submitReview(UUID snapshotId, ReviewRequest request) {
        var snapshot = findSnapshot(snapshotId);
        assertReportIsNotExpired(snapshot);
        var review = reviewRecord(snapshot, ReviewDecision.SUBMITTED, ReportStatus.READY_FOR_REVIEW, request);
        return updateSnapshot(
                snapshot,
                snapshot.sqlVersions(),
                snapshot.aiSuggestions(),
                append(snapshot.reviewRecords(), review),
                append(snapshot.auditEvents(), AuditEvent.of("REVIEW_SUBMITTED", "Report submitted for review by " + request.reviewer() + ".")),
                ReportStatus.READY_FOR_REVIEW,
                snapshot.baselineStatus(),
                snapshot.baselineFrozen()
        );
    }

    public WorkbenchSnapshot approveReview(UUID snapshotId, ReviewRequest request) {
        return approve(snapshotId, request, ReviewDecision.APPROVED, "REVIEW_APPROVED", "Report approved and SQL baseline frozen by ");
    }

    public WorkbenchSnapshot conditionallyApproveReview(UUID snapshotId, ReviewRequest request) {
        return approve(snapshotId, request, ReviewDecision.CONDITIONALLY_APPROVED, "REVIEW_CONDITIONALLY_APPROVED", "Report conditionally approved and SQL baseline frozen by ");
    }

    public WorkbenchSnapshot rejectReview(UUID snapshotId, ReviewRequest request) {
        return closeReview(snapshotId, request, ReviewDecision.REJECTED, ReportStatus.REJECTED, "REVIEW_REJECTED", "Report rejected by ");
    }

    public WorkbenchSnapshot requestChanges(UUID snapshotId, ReviewRequest request) {
        return closeReview(snapshotId, request, ReviewDecision.CHANGES_REQUESTED, ReportStatus.REJECTED, "REVIEW_CHANGES_REQUESTED", "Report changes requested by ");
    }

    public List<SavedSqlVersion> baselineVersions(UUID snapshotId) {
        var snapshot = findSnapshot(snapshotId);
        if (snapshot.reportStatus() != ReportStatus.APPROVED) {
            throw new BadRequestException("Report must be approved before export.");
        }
        if (snapshot.reportStatus() == ReportStatus.EXPIRED || snapshot.baselineStatus() == SqlBaselineStatus.EXPIRED) {
            throw new BadRequestException("Report or SQL baseline is expired.");
        }
        if (!snapshot.baselineFrozen() || snapshot.baselineStatus() != SqlBaselineStatus.BASELINED) {
            throw new BadRequestException("SQL baseline must be frozen before export.");
        }
        return snapshot.sqlVersions().stream()
                .filter(version -> version.status() == SqlBaselineStatus.BASELINED)
                .toList();
    }

    public WorkbenchSnapshot recordExport(UUID snapshotId, String fileName) {
        var snapshot = findSnapshot(snapshotId);
        return updateSnapshot(
                snapshot,
                snapshot.sqlVersions(),
                snapshot.aiSuggestions(),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of("SQL_PACKAGE_EXPORTED", "SQL package exported: " + fileName + ".")),
                snapshot.reportStatus(),
                snapshot.baselineStatus(),
                snapshot.baselineFrozen()
        );
    }

    private WorkbenchSnapshot approve(UUID snapshotId, ReviewRequest request, ReviewDecision decision, String action, String message) {
        var snapshot = findSnapshot(snapshotId);
        assertReadyForReview(snapshot);
        var baselinedVersions = latestVersionsPerStatement(snapshot).stream()
                .map(version -> version.withStatus(SqlBaselineStatus.BASELINED))
                .toList();
        var review = reviewRecord(snapshot, decision, ReportStatus.APPROVED, request);
        return updateSnapshot(
                snapshot,
                baselinedVersions,
                snapshot.aiSuggestions(),
                append(snapshot.reviewRecords(), review),
                append(snapshot.auditEvents(), AuditEvent.of(action, message + request.reviewer() + ".")),
                ReportStatus.APPROVED,
                SqlBaselineStatus.BASELINED,
                true
        );
    }

    private WorkbenchSnapshot closeReview(
            UUID snapshotId,
            ReviewRequest request,
            ReviewDecision decision,
            ReportStatus status,
            String action,
            String message
    ) {
        var snapshot = findSnapshot(snapshotId);
        assertReadyForReview(snapshot);
        var review = reviewRecord(snapshot, decision, status, request);
        return updateSnapshot(
                snapshot,
                snapshot.sqlVersions(),
                snapshot.aiSuggestions(),
                append(snapshot.reviewRecords(), review),
                append(snapshot.auditEvents(), AuditEvent.of(action, message + request.reviewer() + ".")),
                status,
                snapshot.baselineStatus(),
                false
        );
    }

    private WorkbenchSnapshot updateSuggestionStatus(UUID suggestionId, AiSuggestionStatus status, String action, String message) {
        var suggestion = findSuggestion(suggestionId);
        var snapshot = findSnapshot(suggestion.snapshotId());
        return updateSnapshot(
                snapshot,
                snapshot.sqlVersions(),
                replaceSuggestion(snapshot.aiSuggestions(), suggestion.withStatus(status)),
                snapshot.reviewRecords(),
                append(snapshot.auditEvents(), AuditEvent.of(action, message)),
                snapshot.reportStatus(),
                snapshot.baselineStatus(),
                snapshot.baselineFrozen()
        );
    }

    private WorkbenchSnapshot updateSnapshot(
            WorkbenchSnapshot snapshot,
            List<SavedSqlVersion> sqlVersions,
            List<SavedAiSuggestion> suggestions,
            List<ReviewRecord> reviewRecords,
            List<AuditEvent> auditEvents,
            ReportStatus reportStatus,
            SqlBaselineStatus baselineStatus,
            boolean baselineFrozen
    ) {
        return repository.save(new WorkbenchSnapshot(
                snapshot.id(),
                snapshot.createdAt(),
                snapshot.reportVersion(),
                snapshot.originalSql(),
                snapshot.analysis(),
                reportStatus,
                baselineStatus,
                baselineFrozen,
                List.copyOf(sqlVersions),
                List.copyOf(suggestions),
                List.copyOf(reviewRecords),
                List.copyOf(auditEvents)
        ));
    }

    private WorkbenchSnapshot findSnapshot(UUID snapshotId) {
        return repository.findSnapshot(snapshotId)
                .orElseThrow(() -> new NotFoundException("Workbench snapshot not found: " + snapshotId));
    }

    private SavedAiSuggestion findSuggestion(UUID suggestionId) {
        return repository.findSuggestion(suggestionId)
                .orElseThrow(() -> new NotFoundException("AI suggestion not found: " + suggestionId));
    }

    private void assertStatementExists(WorkbenchSnapshot snapshot, int statementIndex) {
        var exists = snapshot.analysis().statements().stream()
                .anyMatch(statement -> statement.index() == statementIndex);
        if (!exists) {
            throw new NotFoundException("Statement not found in snapshot: " + statementIndex);
        }
    }

    private void assertReadyForReview(WorkbenchSnapshot snapshot) {
        if (snapshot.reportStatus() != ReportStatus.READY_FOR_REVIEW) {
            throw new BadRequestException("Report must be submitted for review first.");
        }
        assertReportIsNotExpired(snapshot);
    }

    private void assertReportIsNotExpired(WorkbenchSnapshot snapshot) {
        if (snapshot.reportStatus() == ReportStatus.EXPIRED || snapshot.baselineStatus() == SqlBaselineStatus.EXPIRED) {
            throw new BadRequestException("Expired report or SQL baseline cannot pass the review gate.");
        }
    }

    private ReviewRecord reviewRecord(WorkbenchSnapshot snapshot, ReviewDecision decision, ReportStatus resultingStatus, ReviewRequest request) {
        return new ReviewRecord(
                UUID.randomUUID(),
                snapshot.id(),
                snapshot.reportVersion(),
                decision,
                resultingStatus,
                request.reviewer(),
                request.safeComment(),
                latestVersionsPerStatement(snapshot).stream().map(SavedSqlVersion::id).toList(),
                Instant.now()
        );
    }

    private List<SavedSqlVersion> latestVersionsPerStatement(WorkbenchSnapshot snapshot) {
        var ordered = new ArrayList<SavedSqlVersion>();
        for (var statement : snapshot.analysis().statements()) {
            var latest = snapshot.sqlVersions().stream()
                    .filter(version -> version.statementIndex() == statement.index())
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new NotFoundException("SQL version not found for statement: " + statement.index()));
            ordered.add(latest);
        }
        return List.copyOf(ordered);
    }

    private List<SavedAiSuggestion> replaceSuggestion(List<SavedAiSuggestion> current, SavedAiSuggestion updated) {
        return current.stream()
                .map(suggestion -> suggestion.id().equals(updated.id()) ? updated : suggestion)
                .toList();
    }

    private <T> List<T> append(List<T> current, T value) {
        var copy = new ArrayList<>(current);
        copy.add(value);
        return List.copyOf(copy);
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
