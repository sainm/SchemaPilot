# AI / Vector / MCP Technical Spike

Date: 2026-04-30

## Conclusion

- pgvector is no longer blocked at the SchemaPilot level. The platform now has a JDBC probe endpoint that checks whether the PostgreSQL target has the `vector` extension installed or available.
- Spring AI PgVectorStore is no longer a product blocker. SchemaPilot keeps `KnowledgeService` and `KnowledgeRepository` as the application-facing port, so a PgVectorStore-backed adapter can be added without changing AI, RAG, or report code.
- Spring AI MCP Java SDK is no longer a product blocker. SchemaPilot keeps `McpGateway` as the security boundary and can expose its current resources, prompts, and tools through the SDK later.
- The current default remains safe local fallback: in-memory knowledge store, `LocalEmbeddingAdapter`, MCP allowlist, dry-run write tools, and audit records.

## Official References

- pgvector official repository: https://github.com/pgvector/pgvector
- Spring AI PGVector Store reference: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- Spring AI PgVectorStore API: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/pgvector/PgVectorStore.html
- Spring AI MCP Java SDK reference: https://docs.spring.io/spring-ai-mcp/reference/mcp.html
- Spring AI MCP getting started: https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html

## Implemented Probe Endpoints

- `POST /api/technical-spikes/pgvector/probe`
  - Checks `pg_extension` for installed `vector`.
  - Checks `pg_available_extensions` for available `vector`.
  - Can optionally try `CREATE EXTENSION IF NOT EXISTS vector`.
  - Returns risks and next steps without leaking secrets.

- `GET /api/technical-spikes/spring-ai/pgvector-readiness`
  - Documents the adapter boundary and recommended properties.
  - Keeps Flyway as the owner of SchemaPilot schema creation.
  - Keeps `LocalEmbeddingAdapter` as fallback.

- `GET /api/technical-spikes/spring-ai/mcp-sdk-readiness`
  - Maps current `McpGateway`, resources, prompts, tools, allowlist, dry-run, and audit records to SDK adapter concepts.
  - Preserves production default: external MCP client disabled.

## Local Verification Commands

```powershell
docker compose up -d postgres
psql "postgresql://schemapilot:schemapilot@localhost:5432/schemapilot" -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql "postgresql://schemapilot:schemapilot@localhost:5432/schemapilot" -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"
```

## Tests

- `TechnicalSpikeServiceTest.reportsSpringAiPgVectorReadinessWithoutOwningTableCreation`
- `TechnicalSpikeServiceTest.reportsMcpSdkReadinessPreservingSecurityBoundary`
- `TechnicalSpikeServiceTest.redactsJdbcUrlsFromPgVectorProbeFailures`

Command:

```powershell
.\gradlew.bat test
```

Result: passed.
