package org.sainm.schemapilot.knowledge;

import jakarta.annotation.PostConstruct;
import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import java.util.List;

@Service
public class KnowledgeService {
    private final KnowledgeRepository repository;
    private final SensitiveValueRedactor redactor;

    public KnowledgeService(KnowledgeRepository repository, SensitiveValueRedactor redactor) {
        this.repository = repository;
        this.redactor = redactor;
    }

    @PostConstruct
    public void seedBuiltInKnowledge() {
        if (!repository.findAll().isEmpty()) {
            return;
        }
        addBuiltIn("risk.date", "Oracle DATE semantics", """
                Oracle DATE contains both date and time. PostgreSQL migrations should use timestamp by default, then reviewers can decide whether a business date-only column should be narrowed to date.
                """, Map.of("category", "risk", "riskType", "DATE_SEMANTICS"));
        addBuiltIn("risk.package", "Oracle package migration", """
                Oracle packages combine routines, global state and initialization behavior. PostgreSQL has no direct package object, so split routines and replace state with explicit tables, parameters or settings.
                """, Map.of("category", "risk", "riskType", "PACKAGE"));
        addBuiltIn("risk.anonymous-plsql", "Anonymous PL/SQL block handling", """
                Anonymous Oracle PL/SQL blocks from SQLFILE exports should not be auto-executed. Preserve the original block, flag it as a review item, and convert it to an explicit migration step or routine when needed.
                """, Map.of("category", "risk", "riskType", "ANONYMOUS_PLSQL"));
        addBuiltIn("type.number", "NUMBER type mapping", """
                NUMBER(p,0) can map to integer or bigint when precision fits. NUMBER without precision should stay numeric until source data profiling confirms range and scale.
                """, Map.of("category", "type", "sourceType", "NUMBER"));
        addBuiltIn("type.lob", "LOB type mapping", """
                CLOB maps to text for regular textual payloads. BLOB and RAW map to bytea, with large objects reviewed separately for streaming and memory behavior.
                """, Map.of("category", "type", "sourceType", "LOB"));
        addBuiltIn("function.nvl", "NVL function mapping", """
                NVL can usually be rewritten to COALESCE, but reviewers should check type resolution and side effects because PostgreSQL evaluates expressions with different typing rules.
                """, Map.of("category", "function", "sourceFunction", "NVL"));
        addBuiltIn("function.decode", "DECODE function mapping", """
                DECODE should be rewritten as CASE WHEN expressions. Pay attention to null comparison semantics and result type coercion.
                """, Map.of("category", "function", "sourceFunction", "DECODE"));
    }

    public KnowledgeChunk addChunk(KnowledgeDocumentType type, String key, String title, String content, Map<String, String> metadata, String source, int version) {
        var redactedContent = redactor.redact(content);
        var redactedMetadata = metadata.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> redactor.redact(entry.getKey()), entry -> redactMetadataValue(entry.getKey(), entry.getValue())));
        return repository.save(new KnowledgeChunk(
                UUID.randomUUID(),
                type,
                key,
                redactor.redact(title),
                redactedContent,
                redactedMetadata,
                redactor.redact(source),
                version,
                KnowledgeChunkStatus.ACTIVE,
                Instant.now()
        ));
    }

    public List<KnowledgeChunk> chunks() {
        return repository.findAll();
    }

    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        var queryTerms = terms(request.query());
        return repository.findAll().stream()
                .filter(chunk -> chunk.status() == KnowledgeChunkStatus.ACTIVE || chunk.status() == KnowledgeChunkStatus.REVIEWED)
                .filter(chunk -> metadataMatches(chunk, request.safeMetadata()))
                .map(chunk -> new KnowledgeSearchResult(chunk, score(chunk, queryTerms), excerpt(chunk.content())))
                .filter(result -> result.score() > 0 || !request.safeMetadata().isEmpty())
                .sorted(Comparator.comparingInt(KnowledgeSearchResult::score).reversed())
                .limit(request.safeLimit())
                .toList();
    }

    private void addBuiltIn(String key, String title, String content, Map<String, String> metadata) {
        addChunk(KnowledgeDocumentType.RULE, key, title, content, metadata, "schemapilot-builtin", 1);
    }

    private String redactMetadataValue(String key, String value) {
        var lowered = key.toLowerCase(Locale.ROOT);
        if (lowered.contains("password") || lowered.contains("passwd") || lowered.contains("pwd")
                || lowered.contains("token") || lowered.contains("secret") || lowered.contains("api_key")
                || lowered.contains("apikey") || lowered.contains("access_key")) {
            return "<redacted>";
        }
        return redactor.redact(value);
    }

    private int score(KnowledgeChunk chunk, Set<String> queryTerms) {
        var targetTerms = terms(chunk.title() + " " + chunk.content() + " " + String.join(" ", chunk.metadata().values()));
        var score = 0;
        for (var term : queryTerms) {
            if (targetTerms.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private boolean metadataMatches(KnowledgeChunk chunk, Map<String, String> filters) {
        for (var entry : filters.entrySet()) {
            var value = chunk.metadata().get(entry.getKey());
            if (value == null || !value.equalsIgnoreCase(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Set<String> terms(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(term -> term.length() > 1)
                .collect(Collectors.toSet());
    }

    private String excerpt(String content) {
        var stripped = content.strip();
        return stripped.length() <= 180 ? stripped : stripped.substring(0, 180) + "...";
    }
}
