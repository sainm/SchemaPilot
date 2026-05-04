package org.sainm.schemapilot.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.convert.SqlVersion;
import org.sainm.schemapilot.convert.SqlVersionRepository;
import org.sainm.schemapilot.convert.SqlVersionStatus;
import org.sainm.schemapilot.dependency.DependencyRepository;
import org.sainm.schemapilot.dependency.ObjectDependency;
import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.report.ReportStatus;
import org.sainm.schemapilot.report.StageReport;
import org.sainm.schemapilot.review.ReviewDecision;
import org.sainm.schemapilot.review.ReviewRecord;
import org.sainm.schemapilot.review.ReviewRepository;
import org.sainm.schemapilot.risk.RiskLevel;
import org.sainm.schemapilot.risk.RiskRepository;

public class SqlPackageExportService {

    private final SqlVersionRepository sqlVersionRepository;
    private final ReviewRepository reviewRepository;
    private final RiskRepository riskRepository;
    private final ReportRepository reportRepository;
    private final DependencyRepository dependencyRepository;

    public SqlPackageExportService(
            SqlVersionRepository sqlVersionRepository,
            ReviewRepository reviewRepository,
            RiskRepository riskRepository,
            ReportRepository reportRepository,
            DependencyRepository dependencyRepository) {
        this.sqlVersionRepository = sqlVersionRepository;
        this.reviewRepository = reviewRepository;
        this.riskRepository = riskRepository;
        this.reportRepository = reportRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public SqlPackagePreview preview(UUID projectId) {
        StageReport currentReport = currentReport(projectId);
        ReviewRecord latestReview = latestReview(projectId, currentReport.id());
        List<SqlVersion> baselines = baselineVersions(projectId);
        int blockerCount = blockerCount(projectId);
        validateExportGate(latestReview, baselines, blockerCount);
        return new SqlPackagePreview(
                projectId,
                "READY",
                baselines.size(),
                1,
                blockerCount,
                List.of("001_schema_baseline.sql"),
                baselines.stream().map(version -> version.objectId().toString()).toList());
    }

    public SqlPackage exportSql(UUID projectId) {
        List<SqlVersion> baselines = baselineVersions(projectId);
        preview(projectId);
        StringBuilder content = new StringBuilder();
        content.append("-- SchemaPilot SQL Package\n");
        content.append("-- project: ").append(projectId).append("\n\n");
        for (SqlVersion version : baselines) {
            content.append("-- object: ").append(version.objectId()).append(", version: ").append(version.versionNumber()).append("\n");
            content.append(version.targetSql().strip()).append("\n\n");
        }
        return new SqlPackage(projectId, "001_schema_baseline.sql", content.toString());
    }

    private void validateExportGate(ReviewRecord latestReview, List<SqlVersion> baselines, int blockerCount) {
        if (latestReview == null || !isApproved(latestReview)) {
            throw new BadRequestException("Approved review is required before SQL package export");
        }
        if (baselines.isEmpty()) {
            throw new BadRequestException("Frozen SQL baseline is required before SQL package export");
        }
        if (baselines.stream().anyMatch(this::isManualRequiredBaseline)) {
            throw new BadRequestException("Manual-required SQL must be edited before SQL package export");
        }
        if (baselines.stream().anyMatch(this::isDraftBaseline)) {
            throw new BadRequestException("Draft SQL must be edited before SQL package export");
        }
        if (blockerCount > 0) {
            throw new BadRequestException("BLOCKER risks must be resolved or waived before SQL package export");
        }
    }

    private boolean isManualRequiredBaseline(SqlVersion version) {
        return version.targetSql().stripLeading().startsWith("-- MANUAL_REQUIRED");
    }

    private boolean isDraftBaseline(SqlVersion version) {
        return version.targetSql().stripLeading().startsWith("-- DRAFT");
    }

    private StageReport currentReport(UUID projectId) {
        List<StageReport> reports = reportRepository.findStageReports(projectId);
        if (reports.isEmpty() || reports.getFirst().status() != ReportStatus.CURRENT) {
            throw new BadRequestException("Current precheck report is required before SQL package export");
        }
        return reports.getFirst();
    }

    private ReviewRecord latestReview(UUID projectId, UUID reportId) {
        return reviewRepository.findReviews(projectId).stream()
                .filter(review -> review.reportId().equals(reportId))
                .findFirst()
                .orElse(null);
    }

    private boolean isApproved(ReviewRecord review) {
        return review.decision() == ReviewDecision.APPROVED || review.decision() == ReviewDecision.CONDITIONALLY_APPROVED;
    }

    private List<SqlVersion> baselineVersions(UUID projectId) {
        List<SqlVersion> baselines = sqlVersionRepository.findVersions(projectId).stream()
                .filter(version -> version.status() == SqlVersionStatus.BASELINE)
                .sorted(Comparator.comparing(version -> version.objectId().toString()))
                .toList();
        return orderBaselinesByDependency(projectId, baselines);
    }

    private List<SqlVersion> orderBaselinesByDependency(UUID projectId, List<SqlVersion> baselines) {
        Map<UUID, SqlVersion> baselineByObjectId = new LinkedHashMap<>();
        for (SqlVersion baseline : baselines) {
            baselineByObjectId.putIfAbsent(baseline.objectId(), baseline);
        }
        Map<UUID, Integer> fallbackIndex = new HashMap<>();
        int index = 0;
        for (UUID objectId : baselineByObjectId.keySet()) {
            fallbackIndex.put(objectId, index++);
        }

        Map<UUID, Set<UUID>> dependentsByObjectId = new HashMap<>();
        Map<UUID, Integer> indegreeByObjectId = new HashMap<>();
        for (UUID objectId : baselineByObjectId.keySet()) {
            dependentsByObjectId.put(objectId, new HashSet<>());
            indegreeByObjectId.put(objectId, 0);
        }
        for (ObjectDependency dependency : dependencyRepository.findDependencies(projectId)) {
            UUID dependent = dependency.sourceObjectId();
            UUID prerequisite = dependency.targetObjectId();
            if (!baselineByObjectId.containsKey(dependent)
                    || !baselineByObjectId.containsKey(prerequisite)
                    || dependent.equals(prerequisite)) {
                continue;
            }
            if (dependentsByObjectId.get(prerequisite).add(dependent)) {
                indegreeByObjectId.computeIfPresent(dependent, (ignored, indegree) -> indegree + 1);
            }
        }

        Comparator<UUID> stableOrder = Comparator
                .comparingInt((UUID objectId) -> fallbackIndex.getOrDefault(objectId, Integer.MAX_VALUE))
                .thenComparing(UUID::toString);
        PriorityQueue<UUID> ready = new PriorityQueue<>(stableOrder);
        indegreeByObjectId.forEach((objectId, indegree) -> {
            if (indegree == 0) {
                ready.add(objectId);
            }
        });

        List<SqlVersion> ordered = new ArrayList<>(baselines.size());
        Set<UUID> emitted = new HashSet<>();
        while (!ready.isEmpty()) {
            UUID objectId = ready.poll();
            if (!emitted.add(objectId)) {
                continue;
            }
            ordered.add(baselineByObjectId.get(objectId));
            for (UUID dependent : dependentsByObjectId.getOrDefault(objectId, Set.of())) {
                int nextIndegree = indegreeByObjectId.computeIfPresent(dependent, (ignored, indegree) -> indegree - 1);
                if (nextIndegree == 0) {
                    ready.add(dependent);
                }
            }
        }
        for (SqlVersion baseline : baselines) {
            if (emitted.add(baseline.objectId())) {
                ordered.add(baseline);
            }
        }
        return ordered;
    }

    private int blockerCount(UUID projectId) {
        return (int) riskRepository.findRisks(projectId).stream()
                .filter(risk -> risk.level() == RiskLevel.BLOCKER)
                .count();
    }
}
