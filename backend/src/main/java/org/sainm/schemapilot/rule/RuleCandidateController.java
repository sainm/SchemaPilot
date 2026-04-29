package org.sainm.schemapilot.rule;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rule-candidates")
public class RuleCandidateController {
    private final RuleCandidateService service;

    public RuleCandidateController(RuleCandidateService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<RuleCandidate> create(@Valid @RequestBody RuleCandidateCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping
    public ApiResponse<List<RuleCandidate>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{candidateId}")
    public ApiResponse<RuleCandidate> get(@PathVariable UUID candidateId) {
        return ApiResponse.ok(service.get(candidateId));
    }

    @PostMapping("/{candidateId}/review")
    public ApiResponse<RuleCandidate> review(@PathVariable UUID candidateId, @Valid @RequestBody RuleCandidateReviewRequest request) {
        return ApiResponse.ok(service.review(candidateId, request));
    }

    @PostMapping("/{candidateId}/fixtures")
    public ApiResponse<RuleCandidate> addFixture(@PathVariable UUID candidateId, @Valid @RequestBody RuleFixtureRequest request) {
        return ApiResponse.ok(service.addFixture(candidateId, request));
    }

    @PostMapping("/{candidateId}/test")
    public ApiResponse<List<RuleTestResult>> test(@PathVariable UUID candidateId) {
        return ApiResponse.ok(service.runFixtures(candidateId));
    }

    @PostMapping("/{candidateId}/enable")
    public ApiResponse<RuleEnableResult> enable(@PathVariable UUID candidateId) {
        return ApiResponse.ok(service.enable(candidateId));
    }

    @PostMapping("/reapply")
    public ApiResponse<RuleReapplyResponse> reapply(@Valid @RequestBody RuleReapplyRequest request) {
        return ApiResponse.ok(service.reapply(request));
    }
}
