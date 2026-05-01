# SchemaPilot 架构风险评审闭环

本文只记录设计结论和后续任务，不代表当前版本已经实现。当前策略是先把评审意见沉淀到文档和任务清单，代码实现后续按优先级推进。

## 1. 虚拟线程与连接池背压

风险：

- Java 虚拟线程可以承载大量并发，但 JDBC 连接池、Oracle 会话、PostgreSQL 连接、COPY 通道都是有限资源。
- 如果大量虚拟线程同时等待少量连接，且持有连接期间执行 LOB 读取、FFM 编码、COPY flush、校验等耗时操作，会产生连接饥饿。
- 连接池争用叠加同步块或阻塞驱动代码时，可能放大 Thread Pinning 风险。

设计要求：

- 执行引擎不能把“虚拟线程数量”当作并发上限。
- DDL、COPY、LOB、校验、Sequence Reset、回滚预览等数据库操作必须先获取 execution slot。
- execution slot 应与数据源连接池容量绑定，默认不超过连接池大小的安全比例。
- project/table/global 三层并发限制继续保留，但必须叠加 datasource-level semaphore。

待办：

- 将数据迁移、DDL 执行、校验服务统一接入 datasource execution slot。
- 在数据源配置中记录连接池容量和推荐并发。
- 增加连接等待、slot 等待、执行耗时指标。

## 2. FFM 堆外内存硬水位

风险：

- FFM 堆外内存不受常规 GC 直接管理。
- 如果 shard 异常、arena 未释放、LOB 分块过大或并发过高，可能快速耗尽物理内存。

设计要求：

- 所有 FFM allocation 必须经过 `MemoryBudgetManager`。
- `Arena` 生命周期必须绑定 task/shard，成功、失败、暂停、取消都必须关闭。
- 增加 hard watermark，默认达到 project off-heap budget 的 80% 后暂停新任务或拒绝新 shard 分配。
- 记录 active arena、reserved bytes、hard watermark hit、throttle 次数。

待办：

- 为 `MemoryBudgetManager` 增加硬水位配置。
- 调度器在硬水位触发时暂停新 shard，并生成告警/待处理项。
- 增加 arena 泄漏检测和测试。

## 3. Oracle 空字符串、Sequence、NLS 语义

风险：

- Oracle 将 `''` 视为 `NULL`，PostgreSQL 区分空字符串和 `NULL`。
- 数据迁移完成后，PostgreSQL sequence 可能落后于表中已迁移最大主键。
- Oracle NLS_SORT、NLS_COMP、字符集与 PostgreSQL collation/ctype 不一致时，中文/日文排序和比较可能不同。

设计要求：

- 增加项目级语义配置：默认 `ORACLE_EMPTY_STRING_AS_NULL=true`。
- 数据写入 PostgreSQL 前按配置处理空字符串。
- 所有字符串比较、`IS NULL`、`NVL`、`DECODE`、动态 SQL 继续标记风险。
- 数据校验完成后生成 Sequence Reset 步骤。
- Oracle 扫描阶段采集 NLS 参数，并在目标库创建建议中体现。

待办：

- 增加空字符串策略配置、预检查展示和 COPY 编码应用。
- 增加 sequence reset plan step。
- 增加 NLS/collation 扫描和报告展示。

## 4. AI 与 Agent 预算边界

风险：

- 大型 Oracle package 可能数万行，全文进入 LLM 会导致上下文爆炸、成本失控和回答质量下降。
- Agent 调用 MCP/Skill/解析器失败后，如果没有 step/token budget，可能进入重试循环。

设计要求：

- 大型 PL/SQL/package 不能全文发送给 LLM。
- 处理流程应为：解析 outline、提取函数签名/变量声明、定位高风险切片，再把切片和引用来源交给 AI。
- Agent 必须有 MaxSteps、token/cost budget、tool retry budget。
- 超限后状态转为 `MANUAL_REQUIRED` 或等价人工处理门禁，不能继续自动重试。

待办：

- 引入 PL/SQL AST outline/slice 模型。
- 为 Agent runtime 增加 step budget 和 token/cost budget。
- MCP/Skill 调用增加 retry budget 和失败归因。

## 5. 预处理报告信噪比

风险：

- 大项目可能有几千个对象。
- 如果 LOW 风险逐条展开，DBA 会产生报警疲劳，忽略真正的 BLOCKER。

设计要求：

- 报告摘要必须对重复风险聚类折叠。
- 重复的类型映射风险按对象类型、字段类型、规则来源聚合。
- BLOCKER/HIGH 风险置顶，尤其是 package global variable、autonomous transaction、unsupported DDL、执行失败、校验失败。

待办：

- 增加风险聚类模型。
- 预处理报告增加 folded summary 和 drill-down 明细。
- 前端报告页显示“聚类摘要 + 可展开明细”。

## 6. 回滚与逆向校验

风险：

- DDL 执行中途失败会在目标库留下半成品表、索引、函数、schema。
- 只有 forward SQL 包，没有 rollback/cleanup，会让失败恢复依赖人工临场处理。

设计要求：

- 每个 migration plan 必须生成 forward script 和 undo script。
- DDL step 失败时记录失败对象、失败 SQL、建议清理 SQL。
- 回滚默认 dry-run 预览，正式执行必须经过审核门禁。
- 逆向校验覆盖对象存在、行数、checksum、view 行为、routine 编译、sequence position。

待办：

- 迁移计划增加 undo steps。
- 执行失败后展示 dry-run rollback preview。
- 校验报告增加 sequence position 和回滚可用性检查。

## 7. 后续优先级建议

优先顺序：

1. datasource execution slot 与连接池背压。
2. FFM hard watermark 和 arena 泄漏检测。
3. Sequence Reset 与空字符串策略落地。
4. Agent step/token budget。
5. Undo Script 和 dry-run rollback preview。
6. NLS/collation 扫描。
7. PL/SQL AST outline/slice。
8. 风险聚类折叠。
