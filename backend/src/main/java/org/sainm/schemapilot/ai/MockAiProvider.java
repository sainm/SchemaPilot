package org.sainm.schemapilot.ai;

import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.sainm.schemapilot.sql.PlsqlRewriteAdvisor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
class MockAiProvider implements AiProvider {
    private final AiContextBuilder contextBuilder;
    private final KnowledgeService knowledgeService;
    private final AiGovernanceRegistry governanceRegistry;
    private final PlsqlRewriteAdvisor plsqlRewriteAdvisor = new PlsqlRewriteAdvisor();
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong estimatedInputTokens = new AtomicLong();
    private final AtomicLong estimatedOutputTokens = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> promptCounts = new ConcurrentHashMap<>();

    MockAiProvider(AiContextBuilder contextBuilder, KnowledgeService knowledgeService, AiGovernanceRegistry governanceRegistry) {
        this.contextBuilder = contextBuilder;
        this.knowledgeService = knowledgeService;
        this.governanceRegistry = governanceRegistry;
    }

    @Override
    public AiSuggestionDraft explainRisk(RiskExplanationRequest request) {
        var riskType = request.riskType().toUpperCase(Locale.ROOT);
        var suggestion = switch (riskType) {
            case "NUMBER_PRECISION" -> "Confirm real source data ranges. Use integer or bigint only when all values fit; otherwise keep numeric with reviewed precision.";
            case "DATE_SEMANTICS" -> "Oracle DATE stores date and time. PostgreSQL timestamp is the default target, then business semantics decide whether date is acceptable.";
            case "CURRENT_TIME" -> "Map SYSDATE/SYSTIMESTAMP to CURRENT_TIMESTAMP and review timezone and transaction-time behavior.";
            case "ROWNUM" -> "Rewrite ROWNUM with LIMIT/OFFSET, row_number(), or a CTE depending on ordering semantics.";
            case "CONNECT_BY" -> "Rewrite CONNECT BY as WITH RECURSIVE and verify hierarchy ordering and cycle handling.";
            case "TRIGGER_BODY" -> "Convert Oracle trigger into a PostgreSQL trigger function plus trigger binding, then review NEW/OLD mappings.";
            case "PACKAGE" -> "Split Oracle package routines into functions/procedures and replace package state with explicit tables, parameters, or settings.";
            case "NVL" -> "NVL can often become COALESCE, but review type resolution and side effects.";
            case "EMPTY_STRING_NULL" -> "Oracle treats empty string as NULL; PostgreSQL keeps it distinct. Review predicates, defaults, and uniqueness behavior.";
            case "DYNAMIC_SQL" -> "Recover generated SQL patterns, then rewrite EXECUTE IMMEDIATE with PostgreSQL EXECUTE format(...) USING ... where possible.";
            case "AUTONOMOUS_TRANSACTION" -> "PostgreSQL has no direct in-function autonomous transaction equivalent. Redesign the transaction boundary.";
            default -> "This migration risk requires human review against source SQL, target PostgreSQL version, and business semantics.";
        };
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(riskType, Map.of("riskType", riskType), 3));
        return draft("risk-explanation", suggestion, List.of("riskType=" + riskType, contextBuilder.riskContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request) {
        var suggestion = "Use the generated PostgreSQL SQL as a draft, then review risks: "
                + String.join(", ", request.riskTypes())
                + ". Rewrite ROWNUM with LIMIT/window functions, verify NVL to COALESCE type behavior, and keep PL/SQL changes behind manual review.";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.riskTypes()), Map.of(), 3));
        return draft("sql-rewrite", suggestion, List.of(contextBuilder.sqlRewriteContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request) {
        var suggestion = "This " + request.objectType()
                + " needs PL/pgSQL semantic review. Identify parameters, return values, variables, exception handling, transaction behavior, and dynamic SQL before baseline approval.";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectType(), Map.of(), 3));
        return draft("plsql-explanation", suggestion, List.of(contextBuilder.plsqlContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request) {
        var suggestion = "Precheck found " + request.statementCount() + " statements and " + request.riskCount()
                + " risks, including " + request.highRiskCount() + " high risks and " + request.blockerCount()
                + " blockers. Resolve blockers first, then freeze reviewed SQL baselines before execution.";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.topRiskTypes()), Map.of(), 3));
        return draft("precheck-summary", suggestion, List.of(contextBuilder.precheckContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft diagnoseExecutionError(ExecutionErrorDiagnosisRequest request) {
        var lower = safe(request.errorMessage()).toLowerCase(Locale.ROOT);
        var diagnosis = new ArrayList<String>();
        diagnosis.add("Phase: " + request.phase() + ", object: " + request.objectName() + ".");
        if (lower.contains("syntax")) {
            diagnosis.add("Likely cause: target SQL still contains Oracle syntax or PL/SQL constructs.");
            diagnosis.add("Next step: inspect generated SQL around the reported position and convert it before retry.");
        } else if (lower.contains("relation") && lower.contains("does not exist")) {
            diagnosis.add("Likely cause: dependency order or schema qualification is incomplete.");
            diagnosis.add("Next step: verify migration plan ordering and schema search_path.");
        } else if (lower.contains("duplicate") || lower.contains("unique")) {
            diagnosis.add("Likely cause: target table already contains conflicting rows or source uniqueness differs.");
            diagnosis.add("Next step: compare key columns and decide truncate, upsert, or skip strategy.");
        } else if (lower.contains("copy") || lower.contains("invalid input")) {
            diagnosis.add("Likely cause: COPY encoding, NULL policy, bytea/LOB format, or type conversion mismatch.");
            diagnosis.add("Next step: isolate the failing batch and validate one row against target column types.");
        } else {
            diagnosis.add("Likely cause: execution failed outside known fast-path classifiers.");
            diagnosis.add("Next step: attach SQL, object metadata, and database error position to a work item.");
        }
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.errorMessage(), Map.of(), 3));
        return draft("execution-error-diagnosis", String.join("\n", diagnosis), List.of(contextBuilder.executionErrorContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft diagnoseValidationDiff(ValidationDiffDiagnosisRequest request) {
        var issueCodes = request.issueCodes() == null ? List.<String>of() : request.issueCodes();
        var suggestions = new ArrayList<String>();
        suggestions.add("Validation check " + request.checkType() + " failed for " + request.objectName() + ".");
        if (issueCodes.contains("ROW_COUNT_MISMATCH")) {
            suggestions.add("Check whether COPY skipped failed batches, filters differ, or the target table was not empty.");
        }
        if (issueCodes.contains("SAMPLE_ROWS_MISMATCH")) {
            suggestions.add("Compare ordered sample rows by primary key and inspect date, empty string, NUMBER precision, and bytea encodings.");
        }
        if (issueCodes.contains("SHARD_CHECKSUM_MISMATCH")) {
            suggestions.add("Use the shard index from the validation issue to narrow the data range before row-level comparison.");
        }
        if (issueCodes.contains("VIEW_EXECUTION_FAILED")) {
            suggestions.add("Run the view SQL directly and review unsupported Oracle functions or missing dependencies.");
        }
        if (issueCodes.contains("ROUTINE_COMPILE_FAILED")) {
            suggestions.add("Inspect PL/pgSQL compile errors, parameter modes, exception blocks, and dynamic SQL.");
        }
        if (suggestions.size() == 1) {
            suggestions.add("Compare source and target values, then create a focused work item with object name and check type.");
        }
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", issueCodes), Map.of(), 3));
        return draft("validation-diff-diagnosis", String.join("\n", suggestions), List.of(contextBuilder.validationDiffContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft suggestRuleCandidate(RuleCandidateRequest request) {
        var risks = request.riskTypes() == null ? List.<String>of() : request.riskTypes();
        var suggestion = """
                Rule candidate source: %s
                Scope: objectType=%s, risks=%s
                Pattern: compare the original SQL fragment with the accepted revised SQL and extract the smallest repeatable rewrite.
                Guardrail: keep this candidate disabled until a reviewer approves it and fixture tests cover positive and negative cases.
                Suggested fixture: original SQL as input, revised SQL as expected output, plus one similar SQL that must not match.
                """.formatted(safe(request.source()), safe(request.objectType()), risks);
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", risks), Map.of(), 3));
        return draft("rule-candidate", suggestion.strip(), List.of(contextBuilder.ruleCandidateContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft answerProjectQuestion(ProjectQuestionRequest request) {
        var topRisks = request.topRiskTypes() == null ? List.<String>of() : request.topRiskTypes();
        var objects = request.objectNames() == null ? List.<String>of() : request.objectNames();
        var suggestion = """
                Question: %s
                Project answer: focus first on blockers and high-risk objects, then baseline auto-converted SQL after report approval.
                Top risks: %s
                Objects in scope: %s
                Recommendation: use the precheck report as the gate, execute DDL only after baseline freeze, then validate row counts, samples, and checksums.
                """.formatted(request.question(), topRisks, objects);
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.question() + " " + String.join(" ", topRisks), Map.of(), 3));
        return draft("project-question", suggestion.strip(), List.of(contextBuilder.projectQuestionContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft summarizeLongPlsql(LongPlsqlSummaryRequest request) {
        var chunkSize = Math.max(200, request.chunkSize());
        var source = safe(request.sourceSql());
        var summaries = new ArrayList<String>();
        for (int start = 0; start < source.length(); start += chunkSize) {
            var end = Math.min(source.length(), start + chunkSize);
            summaries.add("chunk " + (summaries.size() + 1) + ": " + summarizeChunk(source.substring(start, end)));
        }
        var suggestion = """
                Long PL/SQL summary for %s (%s)
                %s
                Review focus: package state, dynamic SQL, built-in packages, exception handling, transaction assumptions, and external side effects.
                """.formatted(request.objectName(), safe(request.objectType()), String.join("\n", summaries));
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectType() + " PLSQL", Map.of(), 3));
        return draft("long-plsql-summary", suggestion.strip(), List.of(contextBuilder.longPlsqlContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft planPackageModernization(PackageModernizationRequest request) {
        var routines = plsqlRewriteAdvisor.packageRoutines(request.packageSql());
        var builtins = plsqlRewriteAdvisor.builtinPackageSuggestions(request.packageSql());
        var suggestion = """
                Package modernization plan for %s
                Routines to split: %s
                Built-in package replacements: %s
                State strategy: move package globals to explicit parameters, tables, or controlled session settings.
                Execution strategy: convert stateless routines first, then isolate stateful routines and external side effects behind reviewed adapters.
                Review gate: no generated package replacement should become baseline until compile validation and regression fixtures pass.
                """.formatted(request.objectName(), routines.isEmpty() ? "none detected" : routines, builtins.isEmpty() ? "none detected" : builtins);
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectName() + " PACKAGE", Map.of(), 3));
        return draft("package-modernization", suggestion.strip(), List.of(contextBuilder.packageModernizationContext(request)), chunkKeys(chunks));
    }

    @Override
    public AiSuggestionDraft recommendRuleTemplates(HistoricalRuleTemplateRequest request) {
        var risks = request.riskTypes() == null ? List.<String>of() : request.riskTypes();
        var cases = request.historicalCases() == null ? List.<String>of() : request.historicalCases();
        var templates = new ArrayList<String>();
        for (var risk : risks) {
            var normalized = risk.toUpperCase(Locale.ROOT);
            if ("NVL".equals(normalized)) {
                templates.add("NVL_TO_COALESCE: replace NVL(expr, fallback) with COALESCE(expr, fallback), then verify type inference.");
            } else if ("CURRENT_TIME".equals(normalized) || "DATE_SEMANTICS".equals(normalized)) {
                templates.add("ORACLE_TIME_TO_TIMESTAMP: map SYSDATE/SYSTIMESTAMP and DATE defaults to reviewed PostgreSQL timestamp semantics.");
            } else if ("PACKAGE".equals(normalized)) {
                templates.add("PACKAGE_SPLIT: split stateless package routines first, then isolate package state.");
            } else if ("DYNAMIC_SQL".equals(normalized)) {
                templates.add("EXECUTE_IMMEDIATE_TO_EXECUTE_USING: rewrite generated SQL with EXECUTE format(...) USING ...");
            }
        }
        if (templates.isEmpty()) {
            templates.add("MANUAL_REVIEW_TEMPLATE: create a disabled rule candidate from the accepted SQL diff and require reviewer fixtures.");
        }
        var suggestion = """
                Historical rule template recommendation for objectType=%s
                Historical evidence count: %d
                Recommended templates:
                %s
                Enablement rule: convert each template into a rule candidate, add historical positive and negative fixtures, then require reviewer approval.
                """.formatted(safe(request.objectType()), cases.size(), String.join("\n", templates));
        var chunks = knowledgeService.multiRecall(new KnowledgeSearchRequest(String.join(" ", risks) + " " + String.join(" ", cases), Map.of(), 5));
        return draft("historical-rule-template", suggestion.strip(), List.of(contextBuilder.historicalRuleTemplateContext(request)), chunkKeys(chunks.results()));
    }

    @Override
    public AiUsageStats usageStats() {
        var counts = new LinkedHashMap<String, Long>();
        promptCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue().get()));
        var input = estimatedInputTokens.get();
        var output = estimatedOutputTokens.get();
        return new AiUsageStats(requestCount.get(), input, output, (input * 0.00000015) + (output * 0.00000060), Map.copyOf(counts));
    }

    private AiSuggestionDraft draft(String promptKey, String suggestion, List<String> evidence, List<String> citedChunkKeys) {
        recordUsage(promptKey, suggestion, evidence);
        return new AiSuggestionDraft(
                "mock",
                "schemapilot-rule-backed",
                governanceRegistry.activePromptVersion(promptKey),
                suggestion,
                evidence,
                citedChunkKeys
        );
    }

    private List<String> chunkKeys(List<org.sainm.schemapilot.knowledge.KnowledgeSearchResult> chunks) {
        return chunks.stream().map(result -> result.chunk().key()).toList();
    }

    private void recordUsage(String promptKey, String suggestion, List<String> evidence) {
        requestCount.incrementAndGet();
        promptCounts.computeIfAbsent(promptKey, ignored -> new AtomicLong()).incrementAndGet();
        estimatedInputTokens.addAndGet(estimateTokens(String.join("\n", evidence)));
        estimatedOutputTokens.addAndGet(estimateTokens(suggestion));
    }

    private long estimateTokens(String value) {
        return Math.max(1, (safe(value).length() + 3L) / 4L);
    }

    private String summarizeChunk(String chunk) {
        var lower = chunk.toLowerCase(Locale.ROOT);
        var facts = new ArrayList<String>();
        if (lower.contains("execute immediate")) {
            facts.add("dynamic SQL");
        }
        if (lower.contains("exception") || lower.contains("when others")) {
            facts.add("exception handling");
        }
        if (lower.contains("dbms_") || lower.contains("utl_")) {
            facts.add("Oracle built-in package calls");
        }
        if (lower.contains("procedure ") || lower.contains("function ")) {
            facts.add("routine declarations");
        }
        if (facts.isEmpty()) {
            facts.add("schema logic");
        }
        return String.join(", ", facts);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
