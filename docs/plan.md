# SchemaPilot 实施计划

## 1. 当前基线

当前实现按重新开始处理：代码、旧计划和旧任务清单已删除，只保留 `docs/design.md` 作为设计输入。

本计划以 MVP 方式推进：

- 后端使用 Java 25、Gradle Kotlin DSL、Spring Boot 4.x。
- 后端采用 Gradle 多模块单体：运行时一个 `app`，开发期按迁移能力拆 module。
- 前端使用 React、TypeScript、Vite、Ant Design Pro。
- P0 不做 Oracle 直连执行迁移，不做数据迁移，不做外部参考增强主链路。
- P0 重点是多输入、解析建模、基础依赖、规则转换、AI 上下文建议、报告、审核和 SQL 包导出。

## 2. 交付原则

- 先闭环，再扩展。
- 先结构化事实，再让 AI 解释和建议。
- AI 不能自动覆盖 SQL，不能自动审核，不能自动执行。
- 每个阶段都能导出报告，但只有关键报告承担门禁。
- 所有对象、风险、转换结果和报告都必须可追溯到工程、输入批次、文件路径或手工输入。
- 后端模块边界必须先立住，避免重新回到一个大包里继续堆代码。

## 3. 阶段划分

### P0：MVP 评估转换闭环

目标：让用户从手工 SQL、单文件、多工程、文件夹或 zip 输入开始，完成对象识别、基础依赖、风险识别、转换、AI 辅助建议、报告、审核和 SQL 包导出。

P0 做：

- Gradle 多模块后端骨架。
- 前端工作台骨架。
- 迁移项目和工程单元。
- 手工 SQL 输入。
- 文件、文件夹、zip 输入。
- 输入预检和导入批次对比。
- SQL/PLSQL 切分和对象识别。
- 统一资产模型。
- 基础依赖。
- 规则和风险识别。
- 安全的类型/时间映射策略：`NUMBER` 默认不收窄、`SYSDATE` 不默认写死、空字符串策略显式化。
- Oracle 到 PostgreSQL DDL 转换 MVP。
- AI 上下文构建和 mock provider。
- 转换工作台。
- 阶段报告和预处理报告。
- 报告差异。
- 审核门禁。
- 待处理问题板。
- `STALE` / `WAIVED` 等失效和豁免状态。
- SQL 包预览。
- SQL 包导出。

P0 不做：

- Oracle 直连扫描。
- DDL 真正执行到 PostgreSQL。
- 数据 COPY 迁移。
- 完整 package 自动转换。
- 外部参考增强主链路。
- 外部化 Agent / MCP / Skills。
- 分布式 worker。

### P1：Oracle 直连和结构执行闭环

目标：接入真实 Oracle / PostgreSQL 数据源，把 P0 的 SQL 基线推进到迁移计划和 DDL 执行。

P1 做：

- 数据源管理和连接测试。
- Oracle 元数据扫描。
- Oracle 对象 DDL / PL/SQL 采集。
- Oracle 依赖、统计信息、NLS 信息采集。
- 迁移计划生成。
- DDL dry-run、Undo 预演和执行前门禁。
- Grant/owner 映射和 `SECURITY DEFINER` 安全检查。
- PostgreSQL DDL 执行。
- 执行日志和失败回流。
- Migration Plan Report、Execution Report。

### P2：数据迁移和复杂对象增强

目标：进入数据迁移、校验和复杂 PL/SQL 辅助改造。

P2 做：

- 小表和大表数据迁移。
- `snapshot_scn` / shard manifest 一致性快照。
- PostgreSQL COPY writer 和 bounded buffer。
- Java 25 FFM COPY buffer 特性开关。
- 分片、限速、断点续传。
- 行数、抽样、checksum 校验。
- Sequence Reset、Grant replay、post-load ANALYZE。
- package / trigger / function / procedure 改造增强。
- 对象簇、风险地图、迁移波次建议。

### P3：生产硬化

目标：补齐高并发、回滚、可观测性、安全和企业部署能力。

P3 做：

- 虚拟线程执行 slot 与连接池容量绑定。
- FFM 堆外内存 hard watermark。
- Undo Script 和 dry-run rollback preview。
- NLS / collation 深化。
- 报告聚类折叠。
- Agent / MCP / Skill 后置增强、budget 和失败归因。
- MCP 远程 Streamable HTTP、Origin 校验和长任务资源。
- Spring Batch 是否引入的 ADR。

## 4. P0 模块架构

后端目录目标：

```text
backend
  settings.gradle.kts
  build.gradle.kts
  app
  common
  project
  input
  parser
  model
  dependency
  rule
  convert
  risk
  ai
  report
  review
  export
```

模块职责：

| 模块 | 职责 |
|---|---|
| app | Spring Boot 启动、配置、Controller 聚合、Flyway 脚本 |
| common | ApiResponse、异常、通用工具、基础审计类型 |
| project | 迁移项目、工程单元、项目状态 |
| input | 手工 SQL、文件、文件夹、zip、输入批次 |
| parser | SQL/PLSQL 切分、对象识别、解析问题 |
| model | DbObject、DbColumn、统一资产模型 |
| dependency | 基础依赖和跨工程依赖 |
| rule | 规则定义、规则命中、规则说明 |
| convert | Oracle -> PostgreSQL 转换 |
| risk | 风险识别、风险等级、兼容性评分 |
| ai | AiContextBuilder、AiProvider、AiSuggestion |
| report | 阶段报告、预处理报告 |
| review | 审核门禁、冻结 SQL 基线 |
| export | SQL 包导出 |

依赖方向：

```text
app -> all modules
input -> project + common
parser -> model + common
dependency -> model + common
rule -> model + common
convert -> model + rule + common
risk -> model + rule + dependency + common
ai -> model + dependency + rule + convert + risk + common
report -> project + model + dependency + rule + convert + risk + ai + common
review -> report + common
export -> review + convert + common
```

约束：

- 业务模块不能依赖 `app`。
- `common` 不能依赖业务模块。
- `app` 不写业务逻辑。
- P0 Flyway 脚本先集中在 `app`。
- 不创建独立外部参考增强模块。

## 5. P0 详细步骤

### Step 1：项目骨架

交付：

- 后端 Gradle multi-project。
- Java 25 toolchain。
- `app` Spring Boot 启动模块。
- `common` 和 P0 业务模块。
- 前端 Vite + React + Ant Design Pro。
- PostgreSQL 元数据库配置。
- 健康检查。

验收：

- `.\gradlew.bat projects` 能列出模块。
- `.\gradlew.bat :app:bootRun` 能启动。
- `.\gradlew.bat test` 能跑所有模块测试。
- 前端能调用 `/api/health`。

### Step 2：项目和输入闭环

交付：

- 迁移项目。
- 工程单元 `SourceProject`。
- 输入批次 `InputBatch`。
- 手工 SQL 输入。
- 单文件上传。
- 多文件、文件夹、zip 输入。
- 来源树。
- 输入预检。
- 导入批次对比。

验收：

- 同一迁移项目能包含多个工程单元。
- 每个输入能追踪工程、批次、路径、checksum。
- 空文件、非法 zip、编码失败能生成错误。
- zip bomb、非法路径、超大文件能被预检拦截。
- 同一工程重新导入后能看到文件、对象、风险变化。

### Step 3：解析和资产建模

交付：

- SQL statement splitter。
- PL/SQL block splitter。
- `CREATE TABLE / INDEX / VIEW / SEQUENCE / TRIGGER / FUNCTION / PROCEDURE / PACKAGE` 初步识别。
- `DbObject`、`ParseIssue`。
- 来源位置。

验收：

- 解析失败不丢原文。
- 多工程同名对象不会误合并。
- 对象能按工程过滤和汇总。

### Step 4：基础依赖和规则风险

交付：

- 基础依赖边：view/table、trigger/table、routine/table、package routine。
- 规则定义和规则命中。
- 风险识别。
- 兼容性评分。
- 规则命中解释。
- 待处理问题板。

验收：

- 对象能显示基础依赖和阻塞对象。
- 风险能追溯到规则命中和原 SQL 片段。
- 用户能从风险跳转到规则解释视图。
- ParseIssue、RiskIssue、AI uncertainty、审核意见能汇总成待处理问题。
- P0 不要求完整 Oracle 依赖图。

### Step 5：转换工作台

交付：

- Oracle -> PostgreSQL DDL 转换 MVP。
- 类型、约束、索引、sequence、简单 view 转换。
- trigger/function/procedure/package 草稿或风险。
- SQL 版本链。
- Monaco diff。
- PostgreSQL 语法预检。

验收：

- 用户能编辑目标 SQL。
- 编辑后旧报告和旧基线过期。
- 目标 SQL 可以做语法预检，问题进入风险和待处理问题。
- 审核通过后才能冻结 SQL 基线。

### Step 6：AI 迁移智能层 MVP

交付：

- `AiContextBuilder`。
- `AiProvider`。
- `MockAiProvider`。
- 风险解释。
- SQL 改造建议。
- 验证建议。
- AI 建议保存、接受、忽略、编辑。
- AI 调用审计。

验收：

- AI 输入来自当前项目上下文、对象、依赖、规则命中、风险、转换结果。
- AI 输出包含 `ruleHits`、`affectedObjects`、`changePlan`、`validationPlan`、`uncertainties`。
- AI 不能自动覆盖目标 SQL。
- AI 不能自动通过审核。

### Step 7：报告和审核

交付：

- Source Scan Report。
- Asset Model Report。
- Conversion Report。
- Precheck Report。
- Review Report。
- 报告差异。
- 报告过期检测。
- 审核通过、驳回、有条件通过、请求修改。

验收：

- 阶段报告可导出 HTML/JSON。
- 报告支持按工程单元导出和全项目汇总。
- 重新生成报告能展示新增、删除、变化的对象、风险和 SQL 基线。
- 未审核或报告过期不能导出正式 SQL 包。

### Step 8：SQL 包导出和 P0 回归

交付：

- SQL 包导出。
- SQL 包预览。
- 导出审计。
- P0 fixture。
- P0 端到端回归。

验收：

- 手工 SQL 到 SQL 包闭环。
- 单文件到 SQL 包闭环。
- 多工程/zip 到 SQL 包闭环。
- 修改目标 SQL 后报告过期。
- 未审核不能导出。
- 导出前能预览文件结构、执行顺序、风险摘要和基线版本。

## 6. 版本建议

| 版本 | 目标 |
|---|---|
| 0.1 | 多模块骨架、健康检查、前端骨架 |
| 0.2 | 项目、工程单元、输入批次、手工 SQL |
| 0.3 | 文件/文件夹/zip 输入、解析、对象清单 |
| 0.4 | 基础依赖、规则、风险 |
| 0.5 | 转换工作台、SQL 版本链 |
| 0.6 | AI 上下文、mock AI、建议审计 |
| 0.7 | 阶段报告、预处理报告、审核 |
| 0.8 | SQL 包导出、P0 回归 |

## 7. 关键验收

P0 完成时，用户应该可以：

- 创建迁移项目。
- 添加多个工程单元。
- 输入手工 SQL。
- 上传 SQL 文件、文件夹或 zip。
- 查看工程树、来源树、对象清单。
- 查看基础依赖和阻塞对象。
- 查看风险和规则命中。
- 查看 Oracle SQL 到 PostgreSQL SQL 的转换结果。
- 手工修改目标 SQL。
- 使用 AI 解释风险和生成建议。
- 导出阶段报告。
- 提交审核并冻结 SQL 基线。
- 导出 PostgreSQL SQL 包。

## 8. 暂缓事项

- Oracle 直连扫描。
- 数据迁移。
- 外部参考增强主链路。
- 分布式 worker。
- CDC。
- 自动执行 AI 建议。
- 完整 package 自动转换。
