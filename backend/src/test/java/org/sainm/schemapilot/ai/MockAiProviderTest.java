package org.sainm.schemapilot.ai;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.knowledge.InMemoryKnowledgeRepository;
import org.sainm.schemapilot.knowledge.KnowledgeService;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor();
    private final KnowledgeService knowledgeService = new KnowledgeService(new InMemoryKnowledgeRepository(), redactor);
    private final MockAiProvider provider = new MockAiProvider(
            new AiContextBuilder(redactor),
            knowledgeService,
            new AiGovernanceRegistry("", "gpt-5.4-mini", false, "http://localhost:11434", "qwen2.5-coder:latest")
    );

    MockAiProviderTest() {
        knowledgeService.seedBuiltInKnowledge();
    }

    @Test
    void explainsDateRisk() {
        var response = provider.explainRisk(new RiskExplanationRequest(
                "DATE_SEMANTICS",
                "TABLE",
                "Oracle DATE includes time information.",
                "created_at DATE"
        ));

        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.promptVersion()).isEqualTo("risk-explanation-v1");
        assertThat(response.suggestion()).contains("timestamp");
        assertThat(response.evidence()).anyMatch(value -> value.contains("DATE_SEMANTICS"));
        assertThat(response.citedChunkKeys()).contains("risk.date");
    }

    @Test
    void redactsSensitiveValuesFromEvidence() {
        var response = provider.explainRisk(new RiskExplanationRequest(
                "DYNAMIC_SQL",
                "PROCEDURE",
                "Connection password=secret123 should not leak.",
                "select 'jdbc:oracle:thin:user/pass@db' as dsn from dual"
        ));

        assertThat(String.join("\n", response.evidence()))
                .doesNotContain("secret123")
                .doesNotContain("user/pass@db")
                .contains("<redacted>");
    }

    @Test
    void providesRewritePlsqlAndPrecheckSuggestions() {
        var rewrite = provider.suggestSqlRewrite(new SqlRewriteRequest(
                "VIEW",
                "select NVL(name, 'n/a') from users",
                "select COALESCE(name, 'n/a') from users",
                java.util.List.of("NVL")
        ));
        var plsql = provider.explainPlsqlDraft(new PlsqlExplanationRequest(
                "TRIGGER",
                "trg_users_bi",
                "begin :new.created_at := sysdate; end;",
                "-- draft"
        ));
        var summary = provider.summarizePrecheck(new PrecheckSummaryRequest(10, 3, 1, 0, java.util.List.of("DATE_SEMANTICS")));

        assertThat(rewrite.suggestion()).contains("COALESCE").contains("NVL");
        assertThat(plsql.suggestion()).contains("PL/pgSQL");
        assertThat(summary.suggestion()).contains("10").contains("3");
        assertThat(rewrite.promptVersion()).isEqualTo("sql-rewrite-v1");
    }
}
