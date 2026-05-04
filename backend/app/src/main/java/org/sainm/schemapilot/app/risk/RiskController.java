package org.sainm.schemapilot.app.risk;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.risk.ObjectRiskIssue;
import org.sainm.schemapilot.risk.RiskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/risks")
class RiskController {

    private final RiskRepository riskRepository;

    RiskController(RiskRepository riskRepository) {
        this.riskRepository = riskRepository;
    }

    @GetMapping
    ApiResponse<List<ObjectRiskIssue>> listRisks(@PathVariable UUID projectId) {
        return ApiResponse.success(riskRepository.findRisks(projectId));
    }
}
