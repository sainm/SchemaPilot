package org.sainm.schemapilot.knowledge;

import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTest {
    private final KnowledgeService service = new KnowledgeService(new InMemoryKnowledgeRepository(), new SensitiveValueRedactor(), new LocalEmbeddingAdapter());

    @Test
    void seedsBuiltInRiskTypeAndFunctionKnowledge() {
        service.seedBuiltInKnowledge();

        assertThat(service.chunks())
                .extracting(KnowledgeChunk::key)
                .contains("risk.date", "risk.package", "type.number", "function.nvl", "function.decode");
        assertThat(service.chunks())
                .allMatch(chunk -> chunk.version() == 1)
                .allMatch(chunk -> chunk.status() == KnowledgeChunkStatus.ACTIVE)
                .allMatch(chunk -> chunk.source().equals("schemapilot-builtin"));
    }

    @Test
    void searchesByQueryAndMetadataFilter() {
        service.seedBuiltInKnowledge();

        var results = service.search(new KnowledgeSearchRequest(
                "rewrite nvl to coalesce",
                Map.of("category", "function", "sourceFunction", "NVL"),
                10
        ));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().chunk().key()).isEqualTo("function.nvl");
        assertThat(results.getFirst().score()).isPositive();
        assertThat(results.getFirst().excerpt()).contains("COALESCE");
    }

    @Test
    void redactsSensitiveValuesBeforeKnowledgeStorage() {
        var chunk = service.addChunk(
                KnowledgeDocumentType.PROJECT,
                "project.secret",
                "password=abc123",
                "jdbc:oracle:thin:user/pass@db password=abc123 token=secret-token",
                Map.of("token", "secret-token"),
                "customer-case",
                7
        );

        assertThat(chunk.title()).doesNotContain("abc123").contains("<redacted>");
        assertThat(chunk.content())
                .doesNotContain("user/pass@db")
                .doesNotContain("abc123")
                .doesNotContain("secret-token")
                .contains("<redacted>");
        assertThat(chunk.metadata().values()).allMatch(value -> !value.contains("secret-token"));
        assertThat(chunk.version()).isEqualTo(7);
    }

    @Test
    void multiRecallUsesLexicalMetadataAndLocalEmbeddingThenReranks() {
        service.seedBuiltInKnowledge();
        service.addChunk(
                KnowledgeDocumentType.CASE,
                "case.nvl.account",
                "Account NVL rewrite case",
                "A historical project rewrote NVL to COALESCE in account views.",
                Map.of("category", "historical-case", "riskType", "NVL"),
                "project-a",
                1
        );

        var response = service.multiRecall(new KnowledgeSearchRequest(
                "account coalesce rewrite",
                Map.of("riskType", "NVL"),
                5
        ));

        assertThat(response.results()).isNotEmpty();
        assertThat(response.results().getFirst().chunk().key()).isEqualTo("case.nvl.account");
        assertThat(response.channelHits()).containsKeys("lexical", "metadata", "local-embedding");
        assertThat(service.metrics().hitRate()).isEqualTo(1.0);
    }

    @Test
    void storesHistoricalCaseAfterRedactionAndTracksFeedback() {
        var chunk = service.addHistoricalCase(new HistoricalCaseRequest(
                "case.secret",
                "Customer case password=abc123",
                "Fixed jdbc:oracle:thin:user/pass@db token=secret-token by replacing NVL.",
                Map.of("riskType", "NVL", "token", "secret-token"),
                "project-secret"
        ));

        assertThat(chunk.documentType()).isEqualTo(KnowledgeDocumentType.CASE);
        assertThat(chunk.content())
                .doesNotContain("user/pass@db")
                .doesNotContain("secret-token")
                .contains("<redacted>");
        assertThat(chunk.metadata().get("token")).isEqualTo("<redacted>");

        service.multiRecall(new KnowledgeSearchRequest("replace nvl", Map.of("riskType", "NVL"), 3));
        var metrics = service.feedback(new KnowledgeFeedbackRequest(chunk.key(), true));

        assertThat(metrics.searchCount()).isEqualTo(1);
        assertThat(metrics.hitCount()).isEqualTo(1);
        assertThat(metrics.acceptedCount()).isEqualTo(1);
        assertThat(metrics.adoptionRate()).isEqualTo(1.0);
    }

    @Test
    void localEmbeddingAdapterProducesComparableVectors() {
        var adapter = new LocalEmbeddingAdapter();
        var left = adapter.embed("oracle nvl coalesce rewrite");
        var right = adapter.embed("rewrite nvl to coalesce");
        var unrelated = adapter.embed("large object checksum migration");

        assertThat(adapter.cosine(left, right)).isGreaterThan(adapter.cosine(left, unrelated));
    }
}
