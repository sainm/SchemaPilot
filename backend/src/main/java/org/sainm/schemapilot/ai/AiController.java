package org.sainm.schemapilot.ai;

import org.sainm.schemapilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiProvider aiProvider;
    private final AiGovernanceRegistry governanceRegistry;

    public AiController(AiProvider aiProvider, AiGovernanceRegistry governanceRegistry) {
        this.aiProvider = aiProvider;
        this.governanceRegistry = governanceRegistry;
    }

    @GetMapping("/provider-configs")
    public ApiResponse<List<AiProviderConfig>> providerConfigs() {
        return ApiResponse.ok(governanceRegistry.providerConfigs());
    }

    @GetMapping("/prompt-templates")
    public ApiResponse<List<PromptTemplate>> promptTemplates() {
        return ApiResponse.ok(governanceRegistry.promptTemplates());
    }

    @PostMapping("/explain-risk")
    public ApiResponse<AiSuggestionDraft> explainRisk(@Valid @RequestBody RiskExplanationRequest request) {
        return ApiResponse.ok(aiProvider.explainRisk(request));
    }

    @PostMapping("/suggest-sql")
    public ApiResponse<AiSuggestionDraft> suggestSqlRewrite(@Valid @RequestBody SqlRewriteRequest request) {
        return ApiResponse.ok(aiProvider.suggestSqlRewrite(request));
    }

    @PostMapping("/explain-plsql")
    public ApiResponse<AiSuggestionDraft> explainPlsqlDraft(@Valid @RequestBody PlsqlExplanationRequest request) {
        return ApiResponse.ok(aiProvider.explainPlsqlDraft(request));
    }

    @PostMapping("/summarize-precheck")
    public ApiResponse<AiSuggestionDraft> summarizePrecheck(@Valid @RequestBody PrecheckSummaryRequest request) {
        return ApiResponse.ok(aiProvider.summarizePrecheck(request));
    }
}
