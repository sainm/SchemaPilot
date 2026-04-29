package org.sainm.schemapilot.knowledge;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/chunks")
    public ApiResponse<List<KnowledgeChunk>> chunks() {
        return ApiResponse.ok(knowledgeService.chunks());
    }

    @PostMapping("/search")
    public ApiResponse<List<KnowledgeSearchResult>> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(knowledgeService.search(request));
    }

    @PostMapping("/multi-recall")
    public ApiResponse<KnowledgeMultiRecallResponse> multiRecall(@Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(knowledgeService.multiRecall(request));
    }

    @PostMapping("/historical-cases")
    public ApiResponse<KnowledgeChunk> addHistoricalCase(@Valid @RequestBody HistoricalCaseRequest request) {
        return ApiResponse.ok(knowledgeService.addHistoricalCase(request));
    }

    @PostMapping("/feedback")
    public ApiResponse<KnowledgeMetrics> feedback(@Valid @RequestBody KnowledgeFeedbackRequest request) {
        return ApiResponse.ok(knowledgeService.feedback(request));
    }

    @GetMapping("/metrics")
    public ApiResponse<KnowledgeMetrics> metrics() {
        return ApiResponse.ok(knowledgeService.metrics());
    }
}
