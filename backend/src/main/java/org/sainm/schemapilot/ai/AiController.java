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

    @PostMapping("/diagnose-execution-error")
    public ApiResponse<AiSuggestionDraft> diagnoseExecutionError(@Valid @RequestBody ExecutionErrorDiagnosisRequest request) {
        return ApiResponse.ok(aiProvider.diagnoseExecutionError(request));
    }

    @PostMapping("/diagnose-validation-diff")
    public ApiResponse<AiSuggestionDraft> diagnoseValidationDiff(@Valid @RequestBody ValidationDiffDiagnosisRequest request) {
        return ApiResponse.ok(aiProvider.diagnoseValidationDiff(request));
    }

    @PostMapping("/suggest-rule-candidate")
    public ApiResponse<AiSuggestionDraft> suggestRuleCandidate(@Valid @RequestBody RuleCandidateRequest request) {
        return ApiResponse.ok(aiProvider.suggestRuleCandidate(request));
    }

    @PostMapping("/ask-project")
    public ApiResponse<AiSuggestionDraft> answerProjectQuestion(@Valid @RequestBody ProjectQuestionRequest request) {
        return ApiResponse.ok(aiProvider.answerProjectQuestion(request));
    }

    @PostMapping("/summarize-long-plsql")
    public ApiResponse<AiSuggestionDraft> summarizeLongPlsql(@Valid @RequestBody LongPlsqlSummaryRequest request) {
        return ApiResponse.ok(aiProvider.summarizeLongPlsql(request));
    }

    @PostMapping("/plan-package-modernization")
    public ApiResponse<AiSuggestionDraft> planPackageModernization(@Valid @RequestBody PackageModernizationRequest request) {
        return ApiResponse.ok(aiProvider.planPackageModernization(request));
    }

    @PostMapping("/recommend-rule-templates")
    public ApiResponse<AiSuggestionDraft> recommendRuleTemplates(@Valid @RequestBody HistoricalRuleTemplateRequest request) {
        return ApiResponse.ok(aiProvider.recommendRuleTemplates(request));
    }

    @GetMapping("/usage-stats")
    public ApiResponse<AiUsageStats> usageStats() {
        return ApiResponse.ok(aiProvider.usageStats());
    }

    @GetMapping("/local-status")
    public ApiResponse<LocalLlmStatus> localStatus() {
        if (aiProvider instanceof LocalFirstAiProvider localFirstAiProvider) {
            return ApiResponse.ok(localFirstAiProvider.localStatus());
        }
        return ApiResponse.ok(new LocalLlmStatus(false, "unavailable", "unavailable", false, List.of(), 0, 0, 0, 0, 0, "LOCAL_PROVIDER_NOT_ACTIVE"));
    }
}
