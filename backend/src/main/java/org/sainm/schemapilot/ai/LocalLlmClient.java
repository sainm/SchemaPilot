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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
class LocalLlmClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String endpoint;
    private final String model;
    private final URI chatCompletionsUri;
    private final URI openAiModelsUri;
    private final URI ollamaTagsUri;
    private final Duration timeout;
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicReference<String> lastError = new AtomicReference<>("");

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
        this.endpoint = normalizeEndpoint(endpoint);
        this.model = model;
        this.chatCompletionsUri = endpointUri(this.endpoint, "/v1/chat/completions");
        this.openAiModelsUri = endpointUri(this.endpoint, "/v1/models");
        this.ollamaTagsUri = endpointUri(this.endpoint, "/api/tags");
        this.timeout = timeout;
    }

    String model() {
        return model;
    }

    Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!enabled) {
            return Optional.empty();
        }
        requestCount.incrementAndGet();
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
                recordFailure("HTTP_" + response.statusCode());
                return Optional.empty();
            }
            var content = extractContent(response.body()).filter(value -> !value.isBlank());
            if (content.isEmpty()) {
                recordFailure("EMPTY_COMPLETION");
            } else {
                lastError.set("");
            }
            return content;
        } catch (HttpTimeoutException ex) {
            timeoutCount.incrementAndGet();
            recordFailure("TIMEOUT");
            return Optional.empty();
        } catch (Exception ex) {
            recordFailure(ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    LocalLlmStatus status(long fallbackCount) {
        if (!enabled) {
            return new LocalLlmStatus(false, endpoint, model, false, List.of(), timeout.toMillis(), requestCount.get(), timeoutCount.get(), failureCount.get(), fallbackCount, "DISABLED");
        }
        var models = discoverModels();
        return new LocalLlmStatus(
                true,
                endpoint,
                model,
                !models.isEmpty(),
                models,
                timeout.toMillis(),
                requestCount.get(),
                timeoutCount.get(),
                failureCount.get(),
                fallbackCount,
                lastError.get()
        );
    }

    private List<String> discoverModels() {
        var openAiModels = getOpenAiModels();
        if (!openAiModels.isEmpty()) {
            lastError.set("");
            return openAiModels;
        }
        var ollamaModels = getOllamaModels();
        if (!ollamaModels.isEmpty()) {
            lastError.set("");
            return ollamaModels;
        }
        return List.of();
    }

    private List<String> getOpenAiModels() {
        try {
            var response = httpClient.send(get(openAiModelsUri), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                recordFailure("MODELS_HTTP_" + response.statusCode());
                return List.of();
            }
            var root = objectMapper.readTree(response.body());
            var data = root.path("data");
            var models = new ArrayList<String>();
            if (data.isArray()) {
                for (var item : data) {
                    var id = item.path("id").asText("");
                    if (!id.isBlank()) {
                        models.add(id);
                    }
                }
            }
            return List.copyOf(models);
        } catch (HttpTimeoutException ex) {
            timeoutCount.incrementAndGet();
            recordFailure("MODELS_TIMEOUT");
            return List.of();
        } catch (Exception ex) {
            recordFailure("MODELS_" + ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<String> getOllamaModels() {
        try {
            var response = httpClient.send(get(ollamaTagsUri), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                recordFailure("OLLAMA_TAGS_HTTP_" + response.statusCode());
                return List.of();
            }
            var root = objectMapper.readTree(response.body());
            var data = root.path("models");
            var models = new ArrayList<String>();
            if (data.isArray()) {
                for (var item : data) {
                    var name = item.path("name").asText("");
                    if (!name.isBlank()) {
                        models.add(name);
                    }
                }
            }
            return List.copyOf(models);
        } catch (HttpTimeoutException ex) {
            timeoutCount.incrementAndGet();
            recordFailure("OLLAMA_TAGS_TIMEOUT");
            return List.of();
        } catch (Exception ex) {
            recordFailure("OLLAMA_TAGS_" + ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET()
                .build();
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

    private String normalizeEndpoint(String endpoint) {
        var normalized = endpoint == null || endpoint.isBlank() ? "http://localhost:11434" : endpoint.strip();
        if (normalized.endsWith("/v1/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/v1/chat/completions".length());
        }
        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - "/v1".length());
        }
        return normalized.replaceAll("/+$", "");
    }

    private URI endpointUri(String endpoint, String path) {
        return URI.create(endpoint + path);
    }

    private void recordFailure(String error) {
        failureCount.incrementAndGet();
        lastError.set(error);
    }
}
