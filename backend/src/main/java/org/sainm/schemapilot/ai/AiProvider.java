package org.sainm.schemapilot.ai;

public interface AiProvider {
    AiSuggestionDraft explainRisk(RiskExplanationRequest request);

    AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request);

    AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request);

    AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request);
}
