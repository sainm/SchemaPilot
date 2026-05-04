package org.sainm.schemapilot.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryReportRepository implements ReportRepository {

    private final ConcurrentMap<UUID, StageReport> stageReports = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PrecheckReport> precheckReportsByStageReportId = new ConcurrentHashMap<>();

    @Override
    public void save(StageReport stageReport, PrecheckReport precheckReport) {
        stageReports.put(stageReport.id(), stageReport);
        precheckReportsByStageReportId.put(stageReport.id(), precheckReport);
    }

    @Override
    public List<StageReport> findStageReports(UUID projectId) {
        return stageReports.values().stream()
                .filter(report -> report.projectId().equals(projectId))
                .sorted(Comparator.comparing(StageReport::createdAt).reversed())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public Optional<StageReport> findStageReport(UUID reportId) {
        return Optional.ofNullable(stageReports.get(reportId));
    }

    @Override
    public Optional<PrecheckReport> findPrecheckReport(UUID stageReportId) {
        return Optional.ofNullable(precheckReportsByStageReportId.get(stageReportId));
    }

    @Override
    public void markProjectReportsStale(UUID projectId) {
        stageReports.values().stream()
                .filter(report -> report.projectId().equals(projectId))
                .map(this::toStale)
                .forEach(report -> stageReports.put(report.id(), report));
        precheckReportsByStageReportId.values().stream()
                .filter(report -> report.projectId().equals(projectId))
                .map(this::toStale)
                .forEach(report -> precheckReportsByStageReportId.put(report.stageReportId(), report));
    }

    private StageReport toStale(StageReport report) {
        return new StageReport(
                report.id(),
                report.projectId(),
                report.type(),
                ReportStatus.STALE,
                report.title(),
                staleContent(report.jsonContent()),
                staleContent(report.htmlContent()),
                report.createdAt());
    }

    private PrecheckReport toStale(PrecheckReport report) {
        return new PrecheckReport(
                report.id(),
                report.projectId(),
                report.stageReportId(),
                ReportStatus.STALE,
                report.objectCount(),
                report.parseIssueCount(),
                report.riskCount(),
                report.blockerCount(),
                report.highRiskCount(),
                report.conversionCount(),
                report.sqlVersionCount());
    }

    private String staleContent(String content) {
        return content
                .replace("\"status\": \"CURRENT\"", "\"status\": \"STALE\"")
                .replace("\"status\": \"BLOCKED\"", "\"status\": \"STALE\"")
                .replace("<dt>Status</dt><dd>CURRENT</dd>", "<dt>Status</dt><dd>STALE</dd>")
                .replace("<dt>Status</dt><dd>BLOCKED</dd>", "<dt>Status</dt><dd>STALE</dd>");
    }
}
