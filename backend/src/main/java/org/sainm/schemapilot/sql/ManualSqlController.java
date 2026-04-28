package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manual-sql")
public class ManualSqlController {
    private final ManualSqlAnalysisService analysisService;

    public ManualSqlController(ManualSqlAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public ApiResponse<SqlAnalysisResponse> analyze(@Valid @RequestBody ManualSqlAnalyzeRequest request) {
        return ApiResponse.ok(analysisService.analyze(request.sql()));
    }
}
