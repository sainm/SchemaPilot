package org.sainm.schemapilot.knowledge;

import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTest {
    private final KnowledgeService service = new KnowledgeService(new InMemoryKnowledgeRepository(), new SensitiveValueRedactor());

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
}
