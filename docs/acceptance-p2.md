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

执行命令：

```powershell
.\gradlew.bat test
```

结果：通过。

## 未完成项

- 项目级并发限制当前按目标数据源归组，后续引入正式迁移项目 ID 后可替换控制 key。
- LOB 迁移吞吐基准和数据库真实大 LOB 压测尚未实现。
- checksum 成本验证尚未实现。
