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

## 7. 当前阻塞项

- pgvector、Spring AI PgVectorStore、Spring AI MCP Java SDK 的真实验证需要 Docker/PostgreSQL 或外部依赖环境。
- 当前机器未检测到 `docker` 命令，因此这些验证不能标记完成。
