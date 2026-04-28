# AI / Vector / MCP 技术预研记录

日期：2026-04-28

## 结论

- 本仓库已预留 pgvector 元数据字段、知识 chunk 模型、AI provider 配置、prompt template 版本和 MCP 安全网关。
- `compose.yml` 已选择 `pgvector/pgvector:pg18`，适合作为本地 PostgreSQL + pgvector 验证基础镜像。
- 当前机器未安装 `docker` 命令，因此无法真实启动 PostgreSQL 容器执行 `CREATE EXTENSION vector`、Spring AI PgVectorStore 写入、MCP Java SDK 联调。
- 因此 pgvector / Spring AI PgVectorStore / Spring AI MCP Java SDK 三项现在标记为阻塞，而不是完成。

## 官方资料

- pgvector 官方仓库：<https://github.com/pgvector/pgvector>
- Spring AI PgVector 文档：<https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html>
- Spring AI PgVectorStore API：<https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/pgvector/PgVectorStore.html>
- Spring AI MCP Java SDK：<https://docs.spring.io/spring-ai-mcp/reference/mcp.html>
- Spring AI MCP getting started：<https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html>

## 本地验证命令草案

```powershell
docker compose up -d postgres
psql "postgresql://schemapilot:schemapilot@localhost:5432/schemapilot" -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql "postgresql://schemapilot:schemapilot@localhost:5432/schemapilot" -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"
```

## 后续实现方向

- Spring AI PgVectorStore：在 `knowledge` 模块后面追加 `VectorKnowledgeRepository`，保留当前 in-memory 实现作为无数据库 fallback。
- Embedding：默认先支持本地 OpenAI-compatible embedding endpoint，再支持云端 provider。
- MCP Java SDK：当前自研安全网关先作为内部 adapter，后续接入 Spring AI MCP SDK 时保持 allowlist、timeout、dry-run 和 audit 语义不变。
