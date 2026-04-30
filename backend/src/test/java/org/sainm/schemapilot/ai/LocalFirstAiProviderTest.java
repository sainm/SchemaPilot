package org.sainm.schemapilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.knowledge.InMemoryKnowledgeRepository;
import org.sainm.schemapilot.knowledge.KnowledgeService;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFirstAiProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor();
    private final KnowledgeService knowledgeService = new KnowledgeService(new InMemoryKnowledgeRepository(), redactor);
    private final AiContextBuilder contextBuilder = new AiContextBuilder(redactor);
    private final AiGovernanceRegistry governanceRegistry = new AiGovernanceRegistry(
            "",
            "gpt-5.4-mini",
            false,
            "http://localhost:11434",
            "qwen2.5-coder:latest"
    );
    private final MockAiProvider fallback = new MockAiProvider(contextBuilder, knowledgeService, governanceRegistry);

    LocalFirstAiProviderTest() {
        knowledgeService.seedBuiltInKnowledge();
    }

    @Test
    void usesLocalLlmWhenEnabledAndKeepsPromptRedacted() throws Exception {
        var capturedRequest = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = """
                    {"choices":[{"message":{"content":"Use timestamp and review business date semantics."}}]}
                    """;
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var client = new LocalLlmClient(
                    objectMapper,
                    HttpClient.newHttpClient(),
                    true,
                    "http://localhost:" + server.getAddress().getPort(),
                    "qwen2.5-coder:latest",
                    Duration.ofSeconds(5)
            );
            var provider = new LocalFirstAiProvider(client, fallback, contextBuilder, knowledgeService, governanceRegistry);

            var response = provider.explainRisk(new RiskExplanationRequest(
                    "DATE_SEMANTICS",
                    "TABLE",
                    "password=secret123 must not leak",
                    "select 'jdbc:oracle:thin:user/pass@db' from dual"
            ));

            assertThat(response.provider()).isEqualTo("local-openai-compatible");
            assertThat(response.model()).isEqualTo("qwen2.5-coder:latest");
            assertThat(response.suggestion()).contains("timestamp");
            assertThat(response.citedChunkKeys()).contains("risk.date");
            assertThat(provider.usageStats().promptCounts()).containsKey("local.risk-explanation");
            assertThat(capturedRequest.get())
                    .contains("<redacted>")
                    .doesNotContain("secret123")
                    .doesNotContain("user/pass@db");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToMockWhenLocalLlmIsDisabled() {
        var client = new LocalLlmClient(
                objectMapper,
                HttpClient.newHttpClient(),
                false,
                "http://localhost:11434",
                "qwen2.5-coder:latest",
                Duration.ofMillis(10)
        );
        var provider = new LocalFirstAiProvider(client, fallback, contextBuilder, knowledgeService, governanceRegistry);

        var response = provider.explainRisk(new RiskExplanationRequest(
                "DATE_SEMANTICS",
                "TABLE",
                "Oracle DATE includes time.",
                "created_at DATE"
        ));

        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.suggestion()).contains("timestamp");
    }
}
