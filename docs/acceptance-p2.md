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

## 验证

- `DataMoveServiceTest.movesSmallTableAndRecordsRowsThroughputAndMemory`
- `DataMoveServiceTest.copyFailureCreatesWorkItemAndCanRetry`
- `DataMoveServiceTest.rowCountMismatchCreatesValidationWorkItem`
- `DataMoveServiceTest.largeTableUsesRangeShardsAndCheckpointsCompletedShards`
- `DataMoveServiceTest.largeTableFallsBackToHashShardsWhenBoundsAreUnknown`
- `DataMoveServiceTest.canPauseResumeAndCancelLongRunningMove`

执行命令：

```powershell
.\gradlew.bat test
```

结果：通过。

## 未完成项

- 项目级并发限制当前按目标数据源归组，后续引入正式迁移项目 ID 后可替换控制 key。
- LOB 迁移优化和 LOB 分块 FFM 缓冲尚未实现。
- checksum FFM 分片缓冲和成本验证尚未实现。
