package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/precheck")
public class PrecheckReportController {
    private final PrecheckReportService reportService;

    public PrecheckReportController(PrecheckReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/manual-sql")
    public ApiResponse<PrecheckReportResponse> generate(@Valid @RequestBody PrecheckReportRequest request) {
        return ApiResponse.ok(reportService.generate(request.sql()));
    }
}
