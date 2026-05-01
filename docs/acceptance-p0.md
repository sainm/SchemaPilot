# SchemaPilot P0 手工验收清单

## 1. 启动

- 后端：`cd backend && .\gradlew.bat bootRun --args="--spring.flyway.enabled=false"`
- 前端：`cd frontend && npm run dev`
- 验收：`GET /api/health` 返回 `UP`，并显示 Java 25 与 virtual threads enabled。

## 2. 手工 SQL 到 SQL 包

- 在前端输入 `CREATE TABLE users ...` 示例 SQL。
- 点击 `分析 SQL`，确认对象清单、风险、转换结果出现。
- 点击 `保存快照`，确认生成 SQL 版本链。
- 点击 `生成报告`，确认预处理报告包含对象分布、风险分布、建议顺序。
- 提交审核并审核通过。
- 点击导出 SQL，确认只能导出审核后冻结的 baseline SQL。

## 3. SQL 文件到 SQL 包

- 上传 `.sql` 文件，确认 checksum、encoding、解析进度和对象列表出现。
- 通过 SSE `/api/file-import/jobs/{jobId}/events` 可收到 `file-import-progress` 事件。
- 点击 `保存为快照`，确认文件导入结果进入工作台版本链。
- 提交审核、审核通过并导出 SQL 包。
- 修改目标 SQL 后，确认旧报告或旧 baseline 过期，未重新审核前不能导出。

## 4. AI / RAG

- `GET /api/ai/provider-configs` 返回 mock、cloud、local 三类 provider 预留配置。
- `GET /api/ai/prompt-templates` 返回 active prompt version。
- AI 风险解释或 SQL 建议返回 `promptVersion` 和 `citedChunkKeys`。
- 保存 AI 建议后，版本链中保留 provider、model、promptVersion、inputHash、evidence、citedChunkKeys。
- AI 建议不能自动覆盖目标 SQL，也不能自动通过审核。

## 5. Agent / MCP / Skills

- `GET /api/skills` 返回内置 skill、版本、allowedTools、requiresReview 和 outputSchema。
- `POST /api/agents/run` 能运行 ASSESSMENT、CONVERSION、ERROR_DIAGNOSIS，并记录 steps。
- Agent 运行结束必须停在 `AWAITING_USER_DECISION` 或失败状态，不能绕过审核门禁。
- `GET /api/mcp/status` 显示外部 MCP Client 默认关闭、写工具默认 dry-run、allowlist。
- `POST /api/mcp/tools/call` 对未 allowlist 的工具返回拒绝记录。
- `skill.run` 通过 MCP 默认 dry-run，不能直接执行写类能力。

## 6. 失败场景

- 上传空 SQL 文件返回错误。
- 未审核快照导出 SQL 包返回错误。
- 报告或 baseline 过期后导出 SQL 包返回错误。
- 未识别 SQL 不丢原文，生成 ParseIssue。
- 匿名 PL/SQL 不自动执行，标记风险并保留原文。

## 7. 外部环境验证说明

- pgvector、Spring AI PgVectorStore、Spring AI MCP Java SDK 已不阻塞 SchemaPilot P0 主闭环。
- 当前代码提供 `POST /api/technical-spikes/pgvector/probe`、`GET /api/technical-spikes/spring-ai/pgvector-readiness`、`GET /api/technical-spikes/spring-ai/mcp-sdk-readiness` 作为预研验证入口。
- 本地无 Docker/PostgreSQL 或未安装 pgvector 时，验收方式是确认探针返回风险和下一步建议，而不是把 P0 标记为阻塞。
- 本地 LLM 已通过 `LocalFirstAiProvider` 接入 OpenAI-compatible `/v1/chat/completions`，关闭或不可用时自动 fallback 到 `MockAiProvider`。

## 8. P3 DML/INSERT 导入闭环验收草案

- 上传 INSERT/DML 文件后，平台必须保留原文、checksum、编码、来源行号和解析问题。
- 未列名 INSERT、动态 SQL、LOB literal、大事务和约束冲突必须进入风险清单。
- 目标 schema/table/column 名称必须通过 identifier validator 后才能生成预览或执行计划。
- 未完成目标结构审核、未审核 DML 预检报告、或报告过期时，禁止正式执行。
- 执行失败必须定位到批次和失败行样本，并回流为 work item。
- 导入后必须至少提供行数校验；大批量导入再追加分片 checksum。

## 9. P3 真实环境集成测试 Profile

默认单元测试不连接外部数据库。需要验证真实 PostgreSQL + pgvector 时，在 `backend/` 下设置环境变量后执行 `.\gradlew.bat test`：

```powershell
$env:SCHEMAPILOT_IT_PGVECTOR='true'
$env:SCHEMAPILOT_IT_PG_URL='jdbc:postgresql://127.0.0.1:5432/schemapilot'
$env:SCHEMAPILOT_IT_PG_USERNAME='schemapilot'
$env:SCHEMAPILOT_IT_PG_PASSWORD='schemapilot'
.\gradlew.bat test
```

该 profile 会创建临时表，验证 `CREATE EXTENSION vector` 和 `embedding <=> query` 余弦距离排序可用。
