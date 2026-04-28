# SchemaPilot P1 验收记录

日期：2026-04-29

## Oracle 直连扫描

已实现：

- `POST /api/oracle-scans` 启动扫描任务。
- `GET /api/oracle-scans/{jobId}` 查询扫描快照。
- `GET /api/oracle-scans/{jobId}/events` 通过 SSE 推送 `oracle-scan-progress`。
- JDBC extractor 覆盖 schema、table、column、primary key、foreign key、unique/check constraint、index、sequence、view、trigger、function、procedure、package、synonym、comment、partition metadata。
- DBMS_METADATA 和 ALL_SOURCE 抽取已接入，权限不足时进入 risks。

本地验证：

- `OracleSchemaScanServiceTest` 使用 fake extractor 验证全对象类型、DDL/source、约束、索引、权限风险和进度状态。

外部依赖：

- 真实 Oracle `DBMS_METADATA` / `ALL_SOURCE` 输出仍需要 Oracle 数据库和 Oracle JDBC 驱动环境。

## DDL 迁移计划

已实现：

- 从已审核且冻结的 SQL baseline 生成 migration plan。
- 未审核或过期 baseline 禁止生成正式计划。
- 步骤按 sequence、table、index、view、routine 排序。
- PostgreSQL DDL 执行失败定位到 step、object、SQL version，并生成 work item。

真实验证：

- 使用 PostgreSQL JDBC 连接 `42.193.96.38:5432/lx` 创建临时表 `schemapilot_p1_verify_20260429001252`。
- 计划执行结果：`COMPLETED`。
- 验证后已 drop 临时表。

## 数据迁移 / COPY

已实现：

- `OracleStreamingReader` 抽象和 JDBC 实现。
- `PgJdbcCopyWriter` 使用 pgJDBC `CopyManager`。
- `DataMoveService` 支持小表全量迁移、rows read/write、rows/s、SSE 进度、失败重试、COPY 错误 work item、行数校验 work item。
- FFM COPY buffer 使用 Java 25 `MemorySegment` / `Arena`，记录 off-heap reserved bytes 和 active arena leases。

本地验证：

- `DataMoveServiceTest` 验证小表迁移、COPY 失败定位和重试、行数校验失败回流 work item。
- `FfmCopyBufferTest` 验证 NULL 编码、flush、budget 限流、Arena close 后内存归还。

外部依赖：

- 真实 Oracle 源表到 PostgreSQL COPY 的端到端验证仍需要 Oracle 源库。
