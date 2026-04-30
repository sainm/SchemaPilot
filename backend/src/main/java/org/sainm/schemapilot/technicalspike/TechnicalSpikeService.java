package org.sainm.schemapilot.technicalspike;

import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TechnicalSpikeService {
    private final DataSourceConfigService dataSourceConfigService;
    private final SensitiveValueRedactor redactor;

    public TechnicalSpikeService(DataSourceConfigService dataSourceConfigService, SensitiveValueRedactor redactor) {
        this.dataSourceConfigService = dataSourceConfigService;
        this.redactor = redactor;
    }

    public PgVectorProbeResult probePgVector(PgVectorProbeRequest request) {
        var risks = new ArrayList<String>();
        var nextSteps = new ArrayList<String>();
        var installedVersion = (String) null;
        var availableVersion = (String) null;
        var createSucceeded = false;
        try (var connection = dataSourceConfigService.openConnection(request.dataSourceId())) {
            installedVersion = installedVersion(connection);
            availableVersion = availableVersion(connection);
            if (installedVersion == null && request.tryCreateExtension()) {
                tryCreateExtension(connection);
                createSucceeded = true;
                installedVersion = installedVersion(connection);
            }
        } catch (Exception ex) {
            risks.add("PGVECTOR_PROBE_FAILED:" + sanitize(ex.getMessage()));
        }
        if (installedVersion == null) {
            risks.add("PGVECTOR_EXTENSION_NOT_INSTALLED");
            nextSteps.add("Run CREATE EXTENSION IF NOT EXISTS vector on the metadata database or use the pgvector Docker image.");
        }
        if (availableVersion == null) {
            risks.add("PGVECTOR_EXTENSION_NOT_AVAILABLE");
            nextSteps.add("Install pgvector server files so pg_available_extensions contains vector.");
        }
        nextSteps.add("Keep in-memory LocalEmbeddingAdapter as fallback when pgvector is unavailable.");
        return new PgVectorProbeResult(
                installedVersion != null,
                availableVersion != null,
                installedVersion,
                availableVersion,
                request.tryCreateExtension(),
                createSucceeded,
                List.copyOf(risks),
                List.copyOf(nextSteps)
        );
    }

    public SpringAiPgVectorReadiness springAiPgVectorReadiness() {
        return new SpringAiPgVectorReadiness(
                true,
                "org.springframework.ai:spring-ai-starter-vector-store-pgvector",
                "vector_store",
                "embedding",
                "content",
                "metadata",
                Map.of(
                        "spring.ai.vectorstore.pgvector.initialize-schema", "false",
                        "spring.ai.vectorstore.pgvector.remove-existing-vector-store-table", "false",
                        "spring.ai.vectorstore.pgvector.max-document-batch-size", "10000"
                ),
                List.of(
                        "Keep KnowledgeService and LocalEmbeddingAdapter as the stable application-facing port.",
                        "Add a PgVectorKnowledgeRepository adapter behind the existing KnowledgeRepository contract.",
                        "Use Flyway to own table creation and avoid Spring AI deleting or recreating tables.",
                        "Map KnowledgeChunk content, metadata, source, version, status, and embedding to the vector_store-compatible schema.",
                        "Run pgvector probe before enabling the adapter at startup."
                ),
                List.of(
                        "Embedding dimension must match the selected model.",
                        "Spring AI PgVectorStore table initialization must not bypass SchemaPilot Flyway migrations.",
                        "Metadata filters must preserve existing redaction guarantees."
                )
        );
    }

    public McpSdkReadiness mcpSdkReadiness() {
        return new McpSdkReadiness(
                true,
                "io.modelcontextprotocol.sdk:mcp",
                Map.of(
                        "McpResource", "MCP resource provider",
                        "McpPrompt", "MCP prompt provider",
                        "ToolRegistry", "MCP tool callback registry",
                        "McpGateway allowlist", "SDK tool execution policy",
                        "dry-run default", "write-tool safety guard",
                        "mcp_tool_call audit", "SDK call interceptor/audit sink"
                ),
                List.of(
                        "Keep SchemaPilot McpGateway as the security boundary.",
                        "Add SDK transport adapters after allowlist, timeout, dry-run, and audit tests remain green.",
                        "Expose current resources, prompts, and tools through SDK server APIs.",
                        "Keep external MCP client disabled by default in production profile."
                ),
                List.of(
                        "External MCP tools can exfiltrate context if allowlist and redaction are bypassed.",
                        "Write-capable tools must stay dry-run by default.",
                        "SDK upgrades must be validated against existing McpGatewayTest coverage."
                )
        );
    }

    private String installedVersion(Connection connection) throws java.sql.SQLException {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select extversion from pg_extension where extname = 'vector'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private String availableVersion(Connection connection) throws java.sql.SQLException {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select default_version from pg_available_extensions where name = 'vector'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private void tryCreateExtension(Connection connection) throws java.sql.SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
    }

    private String sanitize(String message) {
        if (message == null) {
            return "unknown";
        }
        return redactor.redact(message);
    }
}
