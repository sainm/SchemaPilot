package org.sainm.schemapilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class LocalLlmClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String model;
    private final URI chatCompletionsUri;
    private final Duration timeout;

    @Autowired
    LocalLlmClient(
            @Value("${schemapilot.ai.enabled:false}") boolean enabled,
            @Value("${schemapilot.ai.local.endpoint:http://localhost:11434}") String endpoint,
            @Value("${schemapilot.ai.local.model:qwen2.5-coder:latest}") String model,
            @Value("${schemapilot.ai.local.timeout-ms:15000}") long timeoutMs
    ) {
        this(new ObjectMapper(), HttpClient.newHttpClient(), enabled, endpoint, model, Duration.ofMillis(timeoutMs));
    }

    LocalLlmClient(ObjectMapper objectMapper, HttpClient httpClient, boolean enabled, String endpoint, String model, Duration timeout) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.enabled = enabled;
        this.model = model;
        this.chatCompletionsUri = chatCompletionsUri(endpoint);
        this.timeout = timeout;
    }

    String model() {
        return model;
    }

    Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
            var request = HttpRequest.newBuilder(chatCompletionsUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return extractContent(response.body()).filter(content -> !content.isBlank());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> extractContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            var choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            var content = choices.get(0).path("message").path("content").asText("");
            return Optional.of(content.strip());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private URI chatCompletionsUri(String endpoint) {
        var normalized = endpoint == null || endpoint.isBlank() ? "http://localhost:11434" : endpoint.strip();
        if (normalized.endsWith("/v1/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized.replaceAll("/+$", "") + "/v1/chat/completions");
    }
}
