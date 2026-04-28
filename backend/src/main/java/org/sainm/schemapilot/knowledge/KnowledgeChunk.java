package org.sainm.schemapilot.knowledge;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeChunk(
        UUID id,
        KnowledgeDocumentType documentType,
        String key,
        String title,
        String content,
        Map<String, String> metadata,
        String source,
        int version,
        KnowledgeChunkStatus status,
        Instant createdAt
) {
}
