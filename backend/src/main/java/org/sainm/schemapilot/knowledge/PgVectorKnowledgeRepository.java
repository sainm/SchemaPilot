package org.sainm.schemapilot.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "schemapilot.knowledge.repository", havingValue = "pgvector")
public class PgVectorKnowledgeRepository implements KnowledgeRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalEmbeddingAdapter embeddingAdapter;

    public PgVectorKnowledgeRepository(JdbcClient jdbcClient, LocalEmbeddingAdapter embeddingAdapter) {
        this.jdbcClient = jdbcClient;
        this.embeddingAdapter = embeddingAdapter;
    }

    @Override
    public KnowledgeChunk save(KnowledgeChunk chunk) {
        var documentId = chunk.id();
        jdbcClient.sql("""
                        insert into knowledge_document (id, source_type, title, source_uri, version, owner, status, created_at, updated_at)
                        values (:id, :sourceType, :title, :sourceUri, :version, :owner, :status, :createdAt, :updatedAt)
                        on conflict (id) do update
                        set source_type = excluded.source_type,
                            title = excluded.title,
                            source_uri = excluded.source_uri,
                            version = excluded.version,
                            owner = excluded.owner,
                            status = excluded.status,
                            updated_at = excluded.updated_at
                        """)
                .param("id", documentId)
                .param("sourceType", chunk.documentType().name())
                .param("title", chunk.title())
                .param("sourceUri", chunk.source())
                .param("version", Integer.toString(chunk.version()))
                .param("owner", "schemapilot")
                .param("status", chunk.status().name())
                .param("createdAt", toOffsetDateTime(chunk.createdAt()))
                .param("updatedAt", toOffsetDateTime(Instant.now()))
                .update();

        jdbcClient.sql("""
                        insert into knowledge_chunk (
                            id, document_id, chunk_type, content, metadata, content_hash, embedding, created_at,
                            chunk_key, title, document_type, source, version_number, status, local_embedding
                        )
                        values (
                            :id, :documentId, :chunkType, :content, cast(:metadata as jsonb), :contentHash, null, :createdAt,
                            :chunkKey, :title, :documentType, :source, :versionNumber, :status, cast(:localEmbedding as vector)
                        )
                        on conflict (id) do update
                        set content = excluded.content,
                            metadata = excluded.metadata,
                            content_hash = excluded.content_hash,
                            chunk_key = excluded.chunk_key,
                            title = excluded.title,
                            document_type = excluded.document_type,
                            source = excluded.source,
                            version_number = excluded.version_number,
                            status = excluded.status,
                            local_embedding = excluded.local_embedding
                        """)
                .param("id", chunk.id())
                .param("documentId", documentId)
                .param("chunkType", chunk.documentType().name())
                .param("content", chunk.content())
                .param("metadata", writeJson(chunk.metadata()))
                .param("contentHash", sha256(chunk.content()))
                .param("createdAt", toOffsetDateTime(chunk.createdAt()))
                .param("chunkKey", chunk.key())
                .param("title", chunk.title())
                .param("documentType", chunk.documentType().name())
                .param("source", chunk.source())
                .param("versionNumber", chunk.version())
                .param("status", chunk.status().name())
                .param("localEmbedding", vectorLiteral(embeddingAdapter.embed(chunk.title() + " " + chunk.content())))
                .update();
        return chunk;
    }

    @Override
    public List<KnowledgeChunk> findAll() {
        return jdbcClient.sql("""
                        select id, chunk_key, title, document_type, content, metadata, source, version_number, status, created_at
                        from knowledge_chunk
                        order by created_at asc, chunk_key asc
                        """)
                .query(this::mapChunk)
                .list();
    }

    @Override
    public boolean supportsVectorSearch() {
        return true;
    }

    @Override
    public List<KnowledgeChunk> findNearestByEmbedding(float[] queryVector, Map<String, String> metadataFilters, int limit) {
        var hasFilters = metadataFilters != null && !metadataFilters.isEmpty();
        var sql = """
                select id, chunk_key, title, document_type, content, metadata, source, version_number, status, created_at
                from knowledge_chunk
                where status in ('ACTIVE', 'REVIEWED')
                  and local_embedding is not null
                """;
        if (hasFilters) {
            sql += "  and metadata @> cast(:metadataFilter as jsonb)\n";
        }
        sql += """
                order by local_embedding <=> cast(:queryVector as vector)
                limit :limit
                """;
        var statement = jdbcClient.sql(sql)
                .param("queryVector", vectorLiteral(queryVector))
                .param("limit", Math.max(1, limit));
        if (hasFilters) {
            statement = statement.param("metadataFilter", writeJson(metadataFilters));
        }
        return statement.query(this::mapChunk).list();
    }

    private KnowledgeChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeChunk(
                rs.getObject("id", UUID.class),
                documentType(rs.getString("document_type")),
                rs.getString("chunk_key"),
                rs.getString("title"),
                rs.getString("content"),
                readMetadata(rs.getString("metadata")),
                rs.getString("source"),
                rs.getInt("version_number"),
                status(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    private KnowledgeDocumentType documentType(String value) {
        try {
            return KnowledgeDocumentType.valueOf(value);
        } catch (RuntimeException ex) {
            return KnowledgeDocumentType.DOCUMENT;
        }
    }

    private KnowledgeChunkStatus status(String value) {
        try {
            return KnowledgeChunkStatus.valueOf(value);
        } catch (RuntimeException ex) {
            return KnowledgeChunkStatus.DRAFT;
        }
    }

    private Map<String, String> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read knowledge metadata", ex);
        }
    }

    private String writeJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write knowledge metadata", ex);
        }
    }

    private String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash knowledge content", ex);
        }
    }

    private String vectorLiteral(float[] vector) {
        var builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[i]));
        }
        return builder.append(']').toString();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
