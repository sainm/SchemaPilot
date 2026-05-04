package org.sainm.schemapilot.app.export;

import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.export.SqlPackage;
import org.sainm.schemapilot.export.SqlPackageExportService;
import org.sainm.schemapilot.export.SqlPackagePreview;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/exports")
class SqlPackageExportController {

    private final SqlPackageExportService sqlPackageExportService;

    SqlPackageExportController(SqlPackageExportService sqlPackageExportService) {
        this.sqlPackageExportService = sqlPackageExportService;
    }

    @GetMapping("/sql-package/preview")
    ApiResponse<SqlPackagePreview> preview(@PathVariable UUID projectId) {
        return ApiResponse.success(sqlPackageExportService.preview(projectId));
    }

    @GetMapping(value = "/sql-package.sql", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> exportSql(@PathVariable UUID projectId) {
        SqlPackage sqlPackage = sqlPackageExportService.exportSql(projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(sqlPackage.fileName())
                        .build()
                        .toString())
                .body(sqlPackage.content());
    }
}
