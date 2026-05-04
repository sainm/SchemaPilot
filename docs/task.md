# SchemaPilot 任务清单

## 1. 状态说明

本任务清单按重新开始后的 MVP 计划生成。

当前基线：

- [x] `P0` 保留 `docs/design.md`。
- [x] `P0` 重新生成 `docs/plan.md`。
- [x] `P0` 重新生成 `docs/task.md`。
- [x] `P0` 后端代码重新创建。
- [x] `P0` 前端代码重新创建。

任务状态：

- `[ ]` 未开始。
- `[~]` 进行中。
- `[x]` 已完成。

完成定义：

- 有接口或页面可验证。
- 有失败场景处理。
- 有必要日志。
- 有测试或 fixture。
- 有版本和来源追踪。
- 不绕过审核门禁。
- 不丢失原始 SQL。
- AI 不自动执行、不自动审核、不自动覆盖 SQL。

## 2. P0：多模块项目骨架

- [x] 创建 `backend/settings.gradle.kts`。
- [x] 创建 `backend/build.gradle.kts` 根构建。
- [x] 配置 Java 25 toolchain。
- [x] 配置 Gradle Kotlin DSL multi-project。
- [x] 创建 `backend/app` 模块。
- [x] 创建 `backend/common` 模块。
- [x] 创建 `backend/project` 模块。
- [x] 创建 `backend/input` 模块。
- [x] 创建 `backend/parser` 模块。
- [x] 创建 `backend/model` 模块。
- [x] 创建 `backend/dependency` 模块。
- [x] 创建 `backend/rule` 模块。
- [x] 创建 `backend/convert` 模块。
- [x] 创建 `backend/risk` 模块。
- [x] 创建 `backend/ai` 模块。
- [x] 创建 `backend/report` 模块。
- [x] 创建 `backend/review` 模块。
- [x] 创建 `backend/export` 模块。
- [x] Spring Boot plugin 只应用到 `app` 模块。
- [x] library 模块使用 `java-library`。
- [x] 配置模块依赖方向。
- [x] 配置 `.\gradlew.bat projects` 可用。
- [x] 配置 `.\gradlew.bat :app:bootRun` 可用。
- [x] 配置 `.\gradlew.bat test` 聚合执行。
- [x] 创建 `/api/health`。
- [x] 配置虚拟线程。
- [x] 配置 `spring.main.keep-alive=true`。
- [~] 配置 PostgreSQL 元数据库连接。
- [~] 配置 Flyway。
- [x] Flyway 脚本先集中在 `app`。
- [x] 创建统一 API 响应。
- [x] 创建统一异常处理。
- [x] 创建基础审计类型。

## 3. P0：前端项目骨架

- [x] 创建 `frontend` Vite 项目。
- [x] 配置 React。
- [x] 配置 TypeScript。
- [x] 配置 Ant Design Pro 或 Ant Design + ProComponents。
- [x] 配置 TanStack Query。
- [x] 配置 Monaco Editor。
- [x] 配置 React Flow。
- [x] 配置 ECharts。
- [x] 创建基础布局。
- [x] 创建左侧菜单。
- [x] 创建 API client。
- [x] 调用 `/api/health`。
- [x] 创建工作台首页。

## 4. P0：项目和工程单元

- [x] 创建 `project` 表。
- [x] 创建 `source_project` 表。
- [x] 创建项目状态枚举。
- [x] 项目状态包含 `STALE`、`PAUSED`、`CANCELLED`。
- [x] 创建工程单元类型枚举：`DATABASE_EXPORT`、`APPLICATION_SQL`、`MANUAL_BATCH`、`TARGET_POSTGRES`。
- [x] 创建项目 API。
- [ ] 创建工程单元 API。
- [x] 创建项目页面。
- [~] 创建工程单元管理页面。
- [~] 支持同一迁移项目下多个工程单元。
- [x] 支持默认工程单元，用于手工 SQL 和单文件。
- [x] 所有后续对象必须带 `project_id` 和 `source_project_id`。

## 5. P0：输入来源和导入批次

- [x] 创建 `input_batch` 表。
- [x] 创建 `input_source` 表。
- [x] 创建输入来源类型枚举。
- [x] 创建手工 SQL 输入 API。
- [x] 创建手工 SQL 输入页面。
- [x] 创建单文件上传 API。
- [x] 创建多文件上传 API。
- [x] 创建 zip 上传 API。
- [~] 创建文件夹导入入口。
- [x] 实现输入预检：最大单文件大小和批次总大小。
- [x] 实现输入预检：zip bomb 检测。
- [x] 实现输入预检：非法路径拦截。
- [x] 实现输入预检：文件类型白名单。
- [x] 实现输入预检：编码识别。
- [ ] 实现输入预检：重复文件 checksum 标记。
- [ ] 实现输入预检：空文件和明显非 SQL 文件识别。
- [x] 保存文件相对路径。
- [x] 保存 checksum。
- [x] 保存文件大小。
- [x] 保存编码检测结果。
- [x] 保存输入批次状态。
- [ ] 保存导入进度。
- [ ] 支持 SSE 推送导入进度。
- [x] 空文件返回错误。
- [x] 非法 zip 返回错误。
- [~] 编码失败生成输入问题。
- [ ] 前端展示工程树。
- [ ] 前端展示来源树。
- [ ] 支持同一工程单元的导入批次对比。
- [ ] 批次对比展示新增、删除、变化文件。
- [ ] 批次对比展示新增、删除、SQL hash 变化对象。
- [ ] 批次对比展示新增、消失、等级变化风险。
- [ ] 批次对比影响报告和基线过期状态。

## 6. P0：解析和资产模型

- [x] 创建 `db_object` 表。
- [x] 创建 `db_column` 表。
- [x] 创建 `parse_issue` 表。
- [x] 创建对象类型枚举。
- [x] 创建对象状态枚举。
- [x] 对象状态包含 `STALE`。
- [x] 实现 SQL statement splitter。
- [x] 实现 PL/SQL block splitter。
- [x] 识别 `CREATE TABLE`。
- [x] 识别 `CREATE INDEX`。
- [x] 识别 `CREATE VIEW`。
- [x] 识别 `CREATE SEQUENCE`。
- [x] 识别 `CREATE TRIGGER`。
- [x] 识别 `CREATE FUNCTION`。
- [x] 识别 `CREATE PROCEDURE`。
- [x] 识别 `CREATE PACKAGE`。
- [x] 识别 `CREATE PACKAGE BODY`。
- [x] 解析失败时保存 `ParseIssue`。
- [x] 解析失败时保留原 SQL。
- [x] 保存对象来源文件路径。
- [x] 保存语句 offset 或行号。
- [x] 保存对象归属工程。
- [x] 多工程同名对象不误合并。
- [x] 对象清单支持按工程过滤。
- [ ] 对象清单支持按对象类型过滤。
- [ ] 对象清单支持按风险等级过滤。

## 7. P0：基础依赖

- [x] 创建 `object_dependency` 表。
- [x] 定义依赖类型枚举。
- [x] 构建 view/table 基础依赖。
- [x] 构建 trigger/table 基础依赖。
- [x] 构建 routine/table 基础依赖。
- [~] 构建 package routine 基础依赖。
- [x] 标记跨工程依赖 `CROSS_SOURCE_DEPENDENCY`。
- [x] 创建基础依赖 API。
- [x] 前端展示基础依赖图。
- [ ] 前端展示阻塞对象。
- [ ] P0 不要求完整 Oracle 直连依赖图。

## 8. P0：规则和风险

- [ ] 创建规则定义模型。
- [x] 创建规则命中模型。
- [x] 创建 `risk_issue` 表。
- [x] 定义风险等级枚举：`LOW`、`MEDIUM`、`HIGH`、`BLOCKER`。
- [x] 检测无精度 `NUMBER`。
- [x] 检测 `NUMBER(10,0)` / `NUMBER(19,0)` 默认收窄风险。
- [x] 检测 Oracle `DATE` 语义差异。
- [x] 检测空字符串和 `NULL` 语义差异。
- [x] 检测 quoted identifier。
- [x] 检测 `ROWNUM`。
- [x] 检测 `CONNECT BY`。
- [x] 检测 `DECODE`。
- [x] 检测 `NVL`。
- [x] 检测 `SYSDATE`。
- [x] `SYSDATE` 风险解释必须区分事务时间、语句时间和真实当前时间。
- [x] 检测 Oracle hint。
- [x] 检测 dynamic SQL。
- [x] 检测 autonomous transaction。
- [x] 检测 package global variable。
- [x] 风险必须引用规则命中。
- [x] 风险必须引用对象和 SQL 片段。
- [~] 创建规则命中解释 API。
- [~] 规则解释包含规则编号、风险等级、原 SQL 片段、语义差异和建议改法。
- [~] 前端提供规则命中解释页或侧边栏。
- [ ] 创建待处理问题模型。
- [ ] 待处理问题支持来源阶段、对象、SQL 版本、严重等级、状态、负责人。
- [ ] 待处理问题状态包含 `WAIVED`，并记录豁免理由、范围和过期条件。
- [ ] ParseIssue 进入待处理问题板。
- [ ] RiskIssue 进入待处理问题板。
- [ ] AI uncertainty 进入待处理问题板。
- [ ] 审核意见进入待处理问题板。
- [ ] 计算对象风险等级。
- [ ] 计算项目兼容性评分。

## 9. P0：转换引擎

- [x] 创建 `conversion_result` 表。
- [x] 定义转换等级枚举：`AUTO`、`REVIEW_REQUIRED`、`DRAFT`、`MANUAL_REQUIRED`、`UNSUPPORTED`。
- [x] 定义 `ObjectConverter`。
- [x] 定义 `ConversionContext`。
- [x] 转换 `VARCHAR2`。
- [x] 转换 `NUMBER(p,s)`。
- [x] `NUMBER(p,0)` 默认转换为 `numeric(p,0)`，不无条件转 `integer` / `bigint`。
- [ ] 只有值域 profile 证明安全时，才生成 `integer` / `bigint` 候选。
- [x] 转换无精度 `NUMBER` 并标风险。
- [x] 转换 `DATE` 并标风险。
- [~] `SYSDATE` 只生成候选改法，不默认写死为 `CURRENT_TIMESTAMP`。
- [ ] 实现 `ORACLE_EMPTY_STRING_AS_NULL` 默认策略和报告展示。
- [x] 转换 `CLOB`。
- [x] 转换 `BLOB`。
- [x] 转换 primary key。
- [x] 转换 unique constraint。
- [x] 转换 check constraint。
- [x] 转换普通 index。
- [x] 转换 sequence。
- [x] 转换简单 view。
- [x] trigger 生成草稿或风险。
- [x] function/procedure 生成草稿或风险。
- [ ] package 生成拆解建议。
- [x] 转换结果引用源对象和规则命中。

## 10. P0：SQL 版本链和转换工作台

- [x] 创建 `sql_version` 表。
- [x] 创建 SQL 基线状态枚举。
- [x] 创建转换工作台 API。
- [x] 创建转换工作台页面。
- [x] 集成 Monaco 左右编辑器。
- [x] 展示 Oracle 原 SQL。
- [x] 展示 PostgreSQL 目标 SQL。
- [ ] 展示 SQL diff。
- [x] 展示转换等级。
- [x] 展示风险问题。
- [x] 展示规则命中。
- [x] 支持用户编辑目标 SQL。
- [x] 保存用户编辑版本。
- [ ] 实现 PostgreSQL 语法预检。
- [ ] 语法预检检查 Oracle 语法残留。
- [ ] 语法预检检查 identifier 非法字符。
- [ ] 语法预检检查明显依赖缺失。
- [ ] 语法预检问题进入风险和待处理问题板。
- [ ] 支持恢复自动生成版本。
- [ ] 修改目标 SQL 后标记旧报告过期。
- [ ] 修改目标 SQL 后标记旧基线过期。
- [ ] 审核通过后冻结 SQL 基线。

## 11. P0：AI 迁移智能层

- [x] 创建 `ai_suggestion` 表。
- [x] 创建 `ai_call_log` 表。
- [x] 定义 `AiProvider`。
- [x] 实现 `MockAiProvider`。
- [x] 定义 `AiContextBuilder`。
- [x] AI 上下文注入对象模型。
- [x] AI 上下文注入基础依赖。
- [x] AI 上下文注入规则命中。
- [x] AI 上下文注入风险问题。
- [x] AI 上下文注入转换结果。
- [ ] AI 上下文注入人工编辑 diff。
- [x] 实现风险解释。
- [x] 实现 SQL 改造建议。
- [x] 实现验证建议。
- [x] 实现报告摘要建议。
- [x] AI 输出包含 `ruleHits`。
- [x] AI 输出包含 `affectedObjects`。
- [x] AI 输出包含 `changePlan`。
- [x] AI 输出包含 `validationPlan`。
- [x] AI 输出包含 `uncertainties`。
- [x] 保存 AI 建议。
- [ ] 支持接受 AI 建议。
- [ ] 支持忽略 AI 建议。
- [ ] 支持编辑后应用 AI 建议。
- [ ] AI 建议应用写审计日志。
- [x] 禁止 AI 自动覆盖 SQL 基线。
- [x] 禁止 AI 自动审核。
- [x] 禁止 AI 自动执行 SQL。

## 12. P0：阶段报告和预处理报告

- [x] 创建 `stage_report` 表。
- [x] 创建 `precheck_report` 表。
- [x] 定义报告状态枚举。
- [x] 定义阶段报告类型：`SOURCE_SCAN`、`ASSET_MODEL`、`CONVERSION`、`PRECHECK`、`REVIEW`。
- [ ] Source Scan Report 导出。
- [ ] Asset Model Report 导出。
- [ ] Conversion Report 导出。
- [x] Precheck Report 导出。
- [ ] Review Report 导出。
- [x] 报告支持 HTML。
- [x] 报告支持 JSON。
- [ ] 报告支持按工程单元导出。
- [x] 报告支持全项目汇总导出。
- [~] 报告引用输入源版本。
- [~] 报告引用对象版本。
- [~] 报告引用转换结果版本。
- [~] 报告引用风险版本。
- [ ] 报告引用 AI 建议版本。
- [ ] 支持报告差异。
- [ ] 报告差异展示新增、删除、变化对象。
- [ ] 报告差异展示新增、消失、等级变化风险。
- [ ] 报告差异展示 SQL 基线变化。
- [ ] BLOCKER 或 SQL 基线变化时旧审核结论失效或要求重新确认。
- [ ] 对象变更后报告过期。
- [ ] 规则变更后报告过期。
- [ ] 目标 SQL 变更后报告过期。
- [ ] 上游变更后相关 AI 建议、Migration Plan 和 SQL 包状态标记 `STALE`。
- [x] 前端报告页。

## 13. P0：审核门禁

- [x] 创建 `review_record` 表。
- [x] 创建审核状态。
- [x] 提交报告审核。
- [x] 审核通过。
- [x] 有条件通过。
- [x] 驳回。
- [x] 请求修改。
- [x] 保存审核意见。
- [x] 保存审核人。
- [x] 保存审核时间。
- [x] 审核记录绑定报告版本。
- [~] 审核记录绑定 SQL 版本。
- [ ] 未审核不能导出正式 SQL 包。
- [ ] 报告过期不能导出正式 SQL 包。
- [ ] SQL 基线过期不能导出正式 SQL 包。
- [x] 审核通过后冻结 SQL 基线。
- [x] P0 支持单用户/开发模式审核。
- [ ] P1 预留角色审核和豁免权限。
- [x] 前端审核页面。

## 14. P0：SQL 包导出

- [x] 创建 SQL 包导出 API。
- [x] 创建 SQL 包预览 API。
- [x] SQL 包预览展示导出文件结构。
- [~] SQL 包预览展示对象数量和对象类型分布。
- [x] SQL 包预览展示 SQL 执行顺序。
- [~] SQL 包预览展示未处理风险和豁免风险摘要。
- [~] SQL 包预览展示 SQL 基线版本、报告版本和审核记录。
- [ ] 按对象类型排序 SQL。
- [x] 只导出审核冻结后的 SQL 基线。
- [x] 支持单个 `.sql` 导出。
- [ ] 支持 zip 导出。
- [ ] 导出前检查报告状态。
- [x] 导出前检查审核状态。
- [x] 导出前检查基线状态。
- [ ] 导出动作写审计日志。
- [x] 前端导出入口。

## 15. P0：MVP 边界检查

- [x] P0 不创建外部化 AgentRuntime。
- [x] P0 不创建 Skill 插件体系。
- [x] P0 不开放 MCP Server / Client。
- [x] P0 AI 只通过 `AiContextBuilder`、`AiProvider`、结构化建议和审计日志进入闭环。
- [x] P0 不引入 Spring Batch，任务状态先用轻量任务表和状态机。
- [x] P0 不启用 FFM 作为硬依赖。

## 16. P0：端到端回归

- [ ] 准备 `CREATE TABLE users` 手工 SQL fixture。
- [ ] 准备 table/index/sequence/view fixture。
- [ ] 准备 trigger/function/procedure/package 风险 fixture。
- [ ] 准备多文件输入 fixture。
- [ ] 准备 zip 输入 fixture。
- [ ] 验证手工 SQL 到 SQL 包闭环。
- [ ] 验证单文件到 SQL 包闭环。
- [ ] 验证 zip 到 SQL 包闭环。
- [ ] 验证解析失败不丢原文。
- [ ] 验证 AI 建议不能自动覆盖 SQL。
- [ ] 验证未审核不能导出。
- [ ] 验证报告过期不能导出。
- [ ] 验证修改目标 SQL 后报告过期。
- [ ] 验证审核通过后冻结 SQL 基线。
- [ ] 验证关键动作都有审计日志。

## 17. P1：Oracle 直连和结构执行

- [ ] 创建 `datasource` 模块。
- [ ] 创建 `metadata` 模块。
- [ ] 创建 `planner` 模块。
- [ ] 创建 `executor` 模块。
- [ ] 创建 `validator` 模块。
- [ ] Oracle 数据源管理。
- [ ] PostgreSQL 数据源管理。
- [ ] 数据源密码加密。
- [ ] Oracle 连接测试。
- [ ] PostgreSQL 连接测试。
- [ ] Oracle schema 扫描。
- [ ] table / column / constraint / index / sequence / view / trigger / routine / package 扫描。
- [ ] `DBMS_METADATA` DDL 采集。
- [ ] PL/SQL source 采集。
- [ ] `ALL_DEPENDENCIES` 或等价依赖采集。
- [ ] row count、segment size、统计信息摘要采集。
- [ ] Oracle 版本、字符集、NLS 参数采集。
- [ ] Oracle 扫描结果接入统一资产模型。
- [ ] 迁移计划生成。
- [ ] 迁移计划包含 `DRY_RUN_DDL`。
- [ ] 迁移计划包含 `DRY_RUN_UNDO`。
- [ ] 迁移计划包含 `REPLAY_GRANT`。
- [ ] 迁移计划包含 `ANALYZE_TARGET` 步骤定义。
- [ ] `SECURITY DEFINER` routine 标记高风险并检查 `search_path`。
- [ ] owner / role / grant 映射缺失时生成 work item。
- [ ] Migration Plan Report 导出。
- [ ] PostgreSQL DDL 执行。
- [ ] DDL Execution Report 导出。
- [ ] DDL 失败回流 work item。

## 18. P2：数据迁移和复杂对象增强

- [ ] 实现 Oracle streaming reader。
- [ ] 实现 PostgreSQL COPY writer。
- [ ] 实现 bounded heap / direct COPY buffer。
- [ ] 生成 `snapshot_scn`。
- [ ] 生成 `DataSnapshotManifest`。
- [ ] Oracle 读取支持 `AS OF SCN`。
- [ ] 无法使用一致性快照时标记非生产或高风险。
- [ ] 实现 Java 25 FFM COPY buffer 特性开关。
- [ ] 实现 MemoryBudgetManager。
- [ ] 小表全量迁移。
- [ ] 大表分片迁移。
- [ ] checkpoint。
- [ ] 断点续传。
- [ ] shard 重试。
- [ ] 行数校验。
- [ ] 抽样校验。
- [ ] checksum 校验。
- [ ] 校验报告区分 `DEMO_ONLY`、`STRUCTURE_READY`、`DATA_READY`、`CUTOVER_READY`、`BLOCKED`。
- [ ] Sequence Reset。
- [ ] Grant replay。
- [ ] post-load ANALYZE。
- [ ] Validation Report 导出。
- [ ] package spec 分析。
- [ ] package body 分析。
- [ ] package routine 拆解。
- [ ] trigger 转换增强。
- [ ] function/procedure 转换增强。
- [ ] 对象簇识别。
- [ ] 风险地图。
- [ ] 迁移波次建议。

## 19. P3：生产硬化

- [ ] 虚拟线程 execution slot 与连接池容量绑定。
- [ ] 连接等待指标。
- [ ] slot 等待指标。
- [ ] FFM 堆外内存 hard watermark。
- [ ] Arena 泄漏检测。
- [ ] Undo Script。
- [ ] dry-run rollback preview。
- [ ] NLS/collation 深化。
- [ ] 报告风险聚类折叠。
- [ ] 大型 PL/SQL AST outline。
- [ ] AI token/cost budget。
- [ ] Agent max step budget。
- [ ] MCP/Skill retry budget。
- [ ] MCP 远程只支持 Streamable HTTP。
- [ ] MCP 远程入口校验 Origin。
- [ ] MCP 长任务支持状态、TTL、取消和结果获取。
- [ ] Spring Batch 引入前完成 ADR。

## 20. 明确不做的 MVP 项

- [ ] 不创建独立外部参考增强模块。
- [ ] 不做 AI 自动执行。
- [ ] 不做 AI 自动审核。
- [ ] 不做完整 package 自动转换。
- [ ] 不做分布式 worker。
- [ ] 不做 CDC。
- [ ] 不做外部化 Agent/MCP/Skills。
