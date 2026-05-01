package org.sainm.schemapilot.ai;

import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeSearchResult;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Primary
@Component
class LocalFirstAiProvider implements AiProvider {
    private static final String SYSTEM_PROMPT = """
            You are SchemaPilot's local Oracle to PostgreSQL migration copilot.
            Use only the redacted project context and cited local knowledge supplied by the application.
            Produce reviewable advice, not executable decisions. Do not approve reports, freeze baselines, or claim execution succeeded.
            Keep the answer concise and include concrete migration review steps.
            """;

    private final LocalLlmClient localLlmClient;
    private final MockAiProvider fallback;
    private final AiContextBuilder contextBuilder;
    private final KnowledgeService knowledgeService;
    private final AiGovernanceRegistry governanceRegistry;
    private final AtomicLong localRequestCount = new AtomicLong();
    private final AtomicLong localFallbackCount = new AtomicLong();
    private final AtomicLong estimatedLocalInputTokens = new AtomicLong();
    private final AtomicLong estimatedLocalOutputTokens = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> localPromptCounts = new ConcurrentHashMap<>();

    LocalFirstAiProvider(
            LocalLlmClient localLlmClient,
            MockAiProvider fallback,
            AiContextBuilder contextBuilder,
            KnowledgeService knowledgeService,
            AiGovernanceRegistry governanceRegistry
    ) {
        this.localLlmClient = localLlmClient;
        this.fallback = fallback;
        this.contextBuilder = contextBuilder;
        this.knowledgeService = knowledgeService;
        this.governanceRegistry = governanceRegistry;
    }

    @Override
    public AiSuggestionDraft explainRisk(RiskExplanationRequest request) {
        var riskType = request.riskType().toUpperCase(java.util.Locale.ROOT);
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(riskType, Map.of("riskType", riskType), 3));
        return localOrFallback(
                "risk-explanation",
                "Explain this migration risk and give reviewer actions.",
                contextBuilder.riskContext(request),
                chunks,
                () -> fallback.explainRisk(request)
        );
    }

    @Override
    public AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.riskTypes()), Map.of(), 3));
        return localOrFallback(
                "sql-rewrite",
                "Suggest a PostgreSQL rewrite strategy. Do not overwrite the generated SQL.",
                contextBuilder.sqlRewriteContext(request),
                chunks,
                () -> fallback.suggestSqlRewrite(request)
        );
    }

    @Override
    public AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectType(), Map.of(), 3));
        return localOrFallback(
                "plsql-explanation",
                "Explain PL/SQL to PL/pgSQL conversion boundaries and review risks.",
                contextBuilder.plsqlContext(request),
                chunks,
                () -> fallback.explainPlsqlDraft(request)
        );
    }

    @Override
    public AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.topRiskTypes()), Map.of(), 3));
        return localOrFallback(
                "precheck-summary",
                "Summarize the precheck report for migration review.",
                contextBuilder.precheckContext(request),
                chunks,
                () -> fallback.summarizePrecheck(request)
        );
    }

    @Override
    public AiSuggestionDraft diagnoseExecutionError(ExecutionErrorDiagnosisRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.errorMessage(), Map.of(), 3));
        return localOrFallback(
                "execution-error-diagnosis",
                "Diagnose this migration execution error and propose next review steps.",
                contextBuilder.executionErrorContext(request),
                chunks,
                () -> fallback.diagnoseExecutionError(request)
        );
    }

    @Override
    public AiSuggestionDraft diagnoseValidationDiff(ValidationDiffDiagnosisRequest request) {
        var issueCodes = request.issueCodes() == null ? List.<String>of() : request.issueCodes();
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", issueCodes), Map.of(), 3));
        return localOrFallback(
                "validation-diff-diagnosis",
                "Diagnose this validation difference and propose investigation steps.",
                contextBuilder.validationDiffContext(request),
                chunks,
                () -> fallback.diagnoseValidationDiff(request)
        );
    }

    @Override
    public AiSuggestionDraft suggestRuleCandidate(RuleCandidateRequest request) {
        var risks = request.riskTypes() == null ? List.<String>of() : request.riskTypes();
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", risks), Map.of(), 3));
        return localOrFallback(
                "rule-candidate",
                "Propose a disabled, human-reviewed conversion rule candidate.",
                contextBuilder.ruleCandidateContext(request),
                chunks,
                () -> fallback.suggestRuleCandidate(request)
        );
    }

    @Override
    public AiSuggestionDraft answerProjectQuestion(ProjectQuestionRequest request) {
        var topRisks = request.topRiskTypes() == null ? List.<String>of() : request.topRiskTypes();
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.question() + " " + String.join(" ", topRisks), Map.of(), 3));
        return localOrFallback(
                "project-question",
                "Answer this project migration question using only supplied local context.",
                contextBuilder.projectQuestionContext(request),
                chunks,
                () -> fallback.answerProjectQuestion(request)
        );
    }

    @Override
    public AiSuggestionDraft summarizeLongPlsql(LongPlsqlSummaryRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectType() + " PLSQL", Map.of(), 3));
        return localOrFallback(
                "long-plsql-summary",
                "Summarize this long PL/SQL object and list conversion review focus.",
                contextBuilder.longPlsqlContext(request),
                chunks,
                () -> fallback.summarizeLongPlsql(request)
        );
    }

    @Override
    public AiSuggestionDraft planPackageModernization(PackageModernizationRequest request) {
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectName() + " PACKAGE", Map.of(), 3));
        return localOrFallback(
                "package-modernization",
                "Create a package modernization plan. Keep it advisory.",
                contextBuilder.packageModernizationContext(request),
                chunks,
                () -> fallback.planPackageModernization(request)
        );
    }

    @Override
    public AiSuggestionDraft recommendRuleTemplates(HistoricalRuleTemplateRequest request) {
        var risks = request.riskTypes() == null ? List.<String>of() : request.riskTypes();
        var cases = request.historicalCases() == null ? List.<String>of() : request.historicalCases();
        var chunks = knowledgeService.multiRecall(new KnowledgeSearchRequest(String.join(" ", risks) + " " + String.join(" ", cases), Map.of(), 5)).results();
        return localOrFallback(
                "historical-rule-template",
                "Recommend reviewed rule templates from historical local knowledge.",
                contextBuilder.historicalRuleTemplateContext(request),
                chunks,
                () -> fallback.recommendRuleTemplates(request)
        );
    }

    @Override
    public AiUsageStats usageStats() {
        var fallbackStats = fallback.usageStats();
        var promptCounts = new LinkedHashMap<String, Long>();
        promptCounts.putAll(fallbackStats.promptCounts());
        localPromptCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> promptCounts.merge("local." + entry.getKey(), entry.getValue().get(), Long::sum));
        return new AiUsageStats(
                localRequestCount.get() + fallbackStats.requestCount(),
                estimatedLocalInputTokens.get() + fallbackStats.estimatedInputTokens(),
                estimatedLocalOutputTokens.get() + fallbackStats.estimatedOutputTokens(),
                fallbackStats.estimatedCostUsd(),
                Map.copyOf(promptCounts)
        );
    }

    public LocalLlmStatus localStatus() {
        return localLlmClient.status(localFallbackCount.get());
    }

    private AiSuggestionDraft localOrFallback(
            String promptKey,
            String task,
            String context,
            List<KnowledgeSearchResult> chunks,
            Supplier<AiSuggestionDraft> fallbackSupplier
    ) {
        var citedChunkKeys = chunks.stream().map(result -> result.chunk().key()).toList();
        var userPrompt = """
                promptKey=%s
                task=%s

                redactedContext:
                %s

                citedLocalKnowledge:
                %s
                """.formatted(promptKey, task, context, localKnowledge(chunks)).strip();
        var localCompletion = localLlmClient.complete(SYSTEM_PROMPT, userPrompt);
        if (localCompletion.isPresent()) {
            var suggestion = localCompletion.get();
            recordLocalUsage(promptKey, userPrompt, suggestion);
            return new AiSuggestionDraft(
                    "local-openai-compatible",
                    localLlmClient.model(),
                    governanceRegistry.activePromptVersion(promptKey),
                    suggestion,
                    List.of(context),
                    citedChunkKeys
            );
        }
        localFallbackCount.incrementAndGet();
        return fallbackSupplier.get();
    }

    private void recordLocalUsage(String promptKey, String input, String output) {
        localRequestCount.incrementAndGet();
        localPromptCounts.computeIfAbsent(promptKey, ignored -> new AtomicLong()).incrementAndGet();
        estimatedLocalInputTokens.addAndGet(estimateTokens(input));
        estimatedLocalOutputTokens.addAndGet(estimateTokens(output));
    }

    private long estimateTokens(String value) {
        return Math.max(1, (value.length() + 3L) / 4L);
    }

    private String localKnowledge(List<KnowledgeSearchResult> chunks) {
        if (chunks.isEmpty()) {
            return "none";
        }
        return chunks.stream()
                .map(result -> "- " + result.chunk().key() + ": " + result.excerpt())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("none");
    }
}
