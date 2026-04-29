package org.sainm.schemapilot.ai;

import java.util.Map;

public interface AiProvider {
    AiSuggestionDraft explainRisk(RiskExplanationRequest request);

    AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request);

    AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request);

    AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request);

    default AiSuggestionDraft diagnoseExecutionError(ExecutionErrorDiagnosisRequest request) {
        return unsupported("execution-error-diagnosis");
    }

    default AiSuggestionDraft diagnoseValidationDiff(ValidationDiffDiagnosisRequest request) {
        return unsupported("validation-diff-diagnosis");
    }

    default AiSuggestionDraft suggestRuleCandidate(RuleCandidateRequest request) {
        return unsupported("rule-candidate");
    }

    default AiSuggestionDraft answerProjectQuestion(ProjectQuestionRequest request) {
        return unsupported("project-question");
    }

    default AiSuggestionDraft summarizeLongPlsql(LongPlsqlSummaryRequest request) {
        return unsupported("long-plsql-summary");
    }

    default AiSuggestionDraft planPackageModernization(PackageModernizationRequest request) {
        return unsupported("package-modernization");
    }

    default AiUsageStats usageStats() {
        return new AiUsageStats(0, 0, 0, 0, Map.of());
    }

    private AiSuggestionDraft unsupported(String promptKey) {
        return new AiSuggestionDraft("unsupported", "unsupported", promptKey + "-v1", "This AI provider does not implement " + promptKey + ".", java.util.List.of(), java.util.List.of());
    }
}
