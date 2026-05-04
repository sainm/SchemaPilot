package org.sainm.schemapilot.app.report;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.convert.ConversionRepository;
import org.sainm.schemapilot.convert.SqlVersionRepository;
import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.report.GeneratedReport;
import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.report.ReportService;
import org.sainm.schemapilot.report.StageReport;
import org.sainm.schemapilot.risk.RiskRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/reports")
class ReportController {

    private final ReportService reportService;
    private final ReportRepository reportRepository;
    private final AssetRepository assetRepository;
    private final RiskRepository riskRepository;
    private final ConversionRepository conversionRepository;
    private final SqlVersionRepository sqlVersionRepository;

    ReportController(
            ReportService reportService,
            ReportRepository reportRepository,
            AssetRepository assetRepository,
            RiskRepository riskRepository,
            ConversionRepository conversionRepository,
            SqlVersionRepository sqlVersionRepository) {
        this.reportService = reportService;
        this.reportRepository = reportRepository;
        this.assetRepository = assetRepository;
        this.riskRepository = riskRepository;
        this.conversionRepository = conversionRepository;
        this.sqlVersionRepository = sqlVersionRepository;
    }

    @PostMapping("/precheck")
    ApiResponse<GeneratedReport> generatePrecheckReport(@PathVariable UUID projectId) {
        GeneratedReport report = reportService.generatePrecheckReport(
                projectId,
                assetRepository.findObjects(projectId),
                assetRepository.findParseIssues(projectId),
                riskRepository.findRisks(projectId),
                conversionRepository.findConversions(projectId),
                sqlVersionRepository.findVersions(projectId));
        return ApiResponse.success(report);
    }

    @GetMapping
    ApiResponse<List<StageReport>> listReports(@PathVariable UUID projectId) {
        return ApiResponse.success(reportRepository.findStageReports(projectId));
    }

    @GetMapping(value = "/{reportId}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> exportJson(@PathVariable UUID projectId, @PathVariable UUID reportId) {
        StageReport report = findReport(projectId, reportId);
        return ResponseEntity.ok(report.jsonContent());
    }

    @GetMapping(value = "/{reportId}/html", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> exportHtml(@PathVariable UUID projectId, @PathVariable UUID reportId) {
        StageReport report = findReport(projectId, reportId);
        return ResponseEntity.ok(report.htmlContent());
    }

    private StageReport findReport(UUID projectId, UUID reportId) {
        return reportRepository.findStageReport(reportId)
                .filter(report -> report.projectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Report not found"));
    }
}
