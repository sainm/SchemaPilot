package org.sainm.schemapilot.app.convert;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.convert.SqlVersion;
import org.sainm.schemapilot.convert.SqlVersionRepository;
import org.sainm.schemapilot.convert.SqlVersionService;
import org.sainm.schemapilot.report.ReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/sql-versions")
class SqlVersionController {

    private final SqlVersionRepository sqlVersionRepository;
    private final SqlVersionService sqlVersionService;
    private final ReportRepository reportRepository;

    SqlVersionController(
            SqlVersionRepository sqlVersionRepository,
            SqlVersionService sqlVersionService,
            ReportRepository reportRepository) {
        this.sqlVersionRepository = sqlVersionRepository;
        this.sqlVersionService = sqlVersionService;
        this.reportRepository = reportRepository;
    }

    @GetMapping
    ApiResponse<List<SqlVersion>> listVersions(@PathVariable UUID projectId) {
        return ApiResponse.success(sqlVersionRepository.findVersions(projectId));
    }

    @PostMapping("/{versionId}/edits")
    ApiResponse<SqlVersion> editVersion(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @RequestBody EditSqlVersionRequest request) {
        SqlVersion edited = sqlVersionService.editVersion(projectId, versionId, request.targetSql());
        reportRepository.markProjectReportsStale(projectId);
        return ApiResponse.success(edited);
    }
}
