package org.sainm.schemapilot.export;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/export")
public class SqlPackageExportController {
    private final SqlPackageExportService exportService;

    public SqlPackageExportController(SqlPackageExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/snapshots/{snapshotId}/sql-package")
    public ApiResponse<SqlPackageResponse> export(@PathVariable UUID snapshotId) {
        return ApiResponse.ok(exportService.export(snapshotId));
    }
}
