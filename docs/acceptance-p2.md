# P2 验收记录

## 高速数据迁移控制面

- 大表识别：`DataMoveService` 根据请求估算行数或 Oracle `count(*)` 结果判断是否进入分片迁移。
- range 分片：存在数值型分片列边界时，`DataMoveShardPlanner` 生成非重叠的 range 谓词。
- hash fallback：无法取得数值边界时，使用 Oracle `ORA_HASH` 生成 hash shard。
- checkpoint：每个 shard 完成后记录下一 shard 下标、累计读写行数和 checkpoint token。
- 断点续传：失败重试从 checkpoint 的下一 shard 继续，而不是从头复制已完成 shard。
- shard 级重试：失败 work item 带 checkpoint，`retry` 保留已完成 shard 进度。
- 并发限制：实现项目、全局和目标表级并发门禁，避免多个 COPY 任务同时压垮源库或目标库。
- 限速：支持按 rows/s 控制迁移速率。
- 暂停/取消/恢复：新增 API 控制长任务生命周期，并通过 SSE 推送状态。
- LOB 迁移优化：Oracle reader 对 CLOB/BLOB 使用独立 `OracleLobValueReader`，避免 `getString` 对大字段的一次性读取路径。
- LOB 分块 FFM 缓冲：`FfmLobChunkBuffer` 使用 Java 25 FFM `Arena` 和 `MemorySegment` 分块读取 CLOB/BLOB，CLOB 输出文本，BLOB 输出 PostgreSQL bytea hex 文本。
- checksum FFM 分片缓冲：新增 `FfmChecksumBuffer`，用于分片校验时以堆外缓冲计算 CRC32，完成后释放 `Arena` 和内存租约。

## 校验闭环

- 对象存在校验：校验源表、目标表是否存在且可读。
- 行数校验：比较源表和目标表行数，失败时生成 `ROW_COUNT_MISMATCH`。
- 抽样校验：按 key column 获取有序样本，失败时生成 `SAMPLE_ROWS_MISMATCH`。
- 分片 checksum：按 key hash 分片计算 checksum，失败时定位到 shard index。
- view 执行校验：用轻量查询验证目标 view 是否可执行。
- routine 编译校验：Oracle 读取 `ALL_OBJECTS` 状态，PostgreSQL 读取 `pg_proc`，验证 routine 是否有效或存在。
- 校验报告：`POST /api/validation-reports` 生成报告，`GET /api/validation-reports/{reportId}` 读取报告。
- 失败详情：报告聚合所有 `ValidationIssue`，包含等级、代码、对象名、shard 和说明。

## PL/SQL 增强

- trigger 转换增强：生成 PostgreSQL trigger function skeleton 和 trigger binding，明确 `:NEW`/`:OLD` 映射审查点。
- function/procedure 转换增强：生成 PL/pgSQL skeleton，保留参数、异常块、dynamic SQL 迁移提示。
- package spec/body 分析：识别 package 与 package body 中的 routine signature、全局状态候选。
- package routine 拆解：报告中列出可拆解为 PostgreSQL function/procedure 的候选 routine。
- Oracle 内置包替代建议：识别 `DBMS_OUTPUT`、`DBMS_LOB`、`DBMS_RANDOM`、`UTL_FILE`、`DBMS_SCHEDULER`、`DBMS_SQL` 并给出替代方向。
- dynamic SQL 标注增强：`EXECUTE IMMEDIATE` 额外生成 bind/format 审查风险。
- exception 语义差异提示：识别 `EXCEPTION`、`NO_DATA_FOUND`、`TOO_MANY_ROWS`、`SQLCODE`、`SQLERRM`、`WHEN OTHERS`。

## AI 增强

- AI 执行错误诊断：`POST /api/ai/diagnose-execution-error` 根据 DDL/COPY/runtime 错误生成原因和下一步建议。
- AI 校验差异排查建议：`POST /api/ai/diagnose-validation-diff` 根据 row count、sample、checksum、view、routine 问题生成排查路径。
- AI 规则沉淀建议：`POST /api/ai/suggest-rule-candidate` 从人工编辑或 AI 建议中生成待审核规则候选说明。
- AI 项目级自然语言问答：`POST /api/ai/ask-project` 基于项目摘要、风险和对象清单回答迁移问题。
- AI 长 PL/SQL 分块摘要：`POST /api/ai/summarize-long-plsql` 按 chunk 汇总长 PL/SQL 的动态 SQL、异常、内置包和 routine 信息。
- AI package 改造方案生成：`POST /api/ai/plan-package-modernization` 生成 package routine 拆分、状态迁移和内置包替代方案。
- AI 成本统计预留：`GET /api/ai/usage-stats` 输出 mock provider 的请求数、估算 token 和估算成本，前端看板待补。

## 规则沉淀闭环

- 从人工编辑 SQL 抽取规则候选：`POST /api/rule-candidates` 支持 `MANUAL_EDIT` 来源。
- 从 AI 建议抽取规则候选：同一入口支持 `AI_SUGGESTION` 来源。
- 人工确认门禁：候选必须通过 `POST /api/rule-candidates/{candidateId}/review` 审核通过后才允许启用。
- 测试样例：`POST /api/rule-candidates/{candidateId}/fixtures` 追加正向或负向 fixture。
- 测试后启用：`POST /api/rule-candidates/{candidateId}/enable` 会运行全部 fixture，失败时进入 `TEST_FAILED`，不会启用。
- 重新转换同类 SQL：`POST /api/rule-candidates/reapply` 只应用 `ENABLED` 且 scope 匹配的规则。
- 来源追溯：候选记录 source、sourceProject、reviewer、riskType、objectType、pattern、replacement。

## 验证

- `DataMoveServiceTest.movesSmallTableAndRecordsRowsThroughputAndMemory`
- `DataMoveServiceTest.copyFailureCreatesWorkItemAndCanRetry`
- `DataMoveServiceTest.rowCountMismatchCreatesValidationWorkItem`
- `DataMoveServiceTest.largeTableUsesRangeShardsAndCheckpointsCompletedShards`
- `DataMoveServiceTest.largeTableFallsBackToHashShardsWhenBoundsAreUnknown`
- `DataMoveServiceTest.canPauseResumeAndCancelLongRunningMove`
- `OracleLobValueReaderTest.readsClobThroughFfmChunksAndReleasesMemory`
- `OracleLobValueReaderTest.readsBlobAsPostgresByteaHexThroughFfmChunks`
- `OracleLobValueReaderTest.readsByteArrayAsPostgresByteaHex`
- `ValidationReportServiceTest.createsPassingValidationReport`
- `ValidationReportServiceTest.reportsRowCountSampleChecksumViewAndRoutineFailures`
- `ValidationReportServiceTest.ffmChecksumBufferReleasesOffHeapMemory`
- `ManualSqlAnalysisServiceTest.keepsTriggerBlockAsSingleDraftStatement`
- `ManualSqlAnalysisServiceTest.enhancesPlsqlRoutineAndBuiltinPackageSuggestions`
- `ManualSqlAnalysisServiceTest.analyzesPackageBodyRoutinesAndReplacementHints`
- `MockAiProviderTest.diagnosesExecutionErrorsAndValidationDiffs`
- `MockAiProviderTest.suggestsRulesAnswersProjectQuestionsAndTracksUsage`
- `MockAiProviderTest.summarizesLongPlsqlAndPlansPackageModernization`
- `RuleCandidateServiceTest.extractsManualEditRuleRequiresReviewAndReappliesAfterFixturesPass`
- `RuleCandidateServiceTest.rejectedCandidateDoesNotEnable`
- `RuleCandidateServiceTest.fixtureFailurePreventsEnablement`

执行命令：

```powershell
.\gradlew.bat test
```

结果：通过。

## 未完成项

- 项目级并发限制当前按目标数据源归组，后续引入正式迁移项目 ID 后可替换控制 key。
- LOB 迁移吞吐基准和数据库真实大 LOB 压测尚未实现。
- checksum 成本验证尚未实现。
