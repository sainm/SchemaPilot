package org.sainm.schemapilot.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository {

    void save(StageReport stageReport, PrecheckReport precheckReport);

    List<StageReport> findStageReports(UUID projectId);

    Optional<StageReport> findStageReport(UUID reportId);

    Optional<PrecheckReport> findPrecheckReport(UUID stageReportId);

    void markProjectReportsStale(UUID projectId);
}
