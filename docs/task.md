# SchemaPilot 任务清单

## 1. 状态说明

- `[ ]` 未开始
- `[~]` 进行中
- `[x]` 已完成
- `[!]` 阻塞

优先级：

- `P0`：第一条评估转换闭环必须完成。
- `P1`：结构执行和基础数据迁移。
- `P2`：高速迁移、复杂对象、企业化增强。
- `P3`：生产硬化、治理、真实环境联调。

闭环验收要求：

- 每个闭环必须有入口、过程、产物、门禁、回流。
- 每个产物必须能追溯来源和版本。
- 每个门禁必须有测试证明不能绕过。
- 每个失败路径必须能形成待处理问题，而不是只写日志。

## 2. 当前已完成

- [x] `P0` 创建设计文档。
- [x] `P0` 创建实施计划文档。
- [x] `P0` 创建任务清单文档。
- [x] `P0` 明确 Java 25 + 虚拟线程方向。
- [x] `P1` 明确 Java 25 FFM 用于 COPY、LOB、文件解析等堆外缓冲控制。
- [x] `P0` 明确预处理报告和审核为核心门禁。
- [x] `P0` 明确 AI 作为迁移副驾驶，不直接替代规则引擎和人工审核。
- [x] `P0` 明确本地 LLM + 本地知识库为私有化默认目标，云端 provider 仅显式启用。
- [x] `P0` 明确资产、转换、评审、执行、校验、规则沉淀闭环。

## 3. P0：项目骨架

- [x] `P0` 初始化 Git 仓库。
- [x] `P0` 创建 `backend/` Spring Boot 4.x 项目。
- [x] `P0` 配置 Java 25。
- [x] `P0` 启用虚拟线程。
- [x] `P0` 配置 `spring.main.keep-alive=true`。
- [x] `P0` 添加健康检查接口。
- [x] `P0` 添加统一异常处理。
- [x] `P0` 添加统一 API 返回结构。
- [x] `P0` 添加 PostgreSQL 元数据库连接。
- [x] `P0` 添加 Flyway 或 Liquibase。
- [x] `P0` 创建 `frontend/` React + TypeScript + Vite 项目。
- [x] `P0` 接入 Ant Design Pro。
- [x] `P0` 前端调用后端 health 接口。

## 4. P0：核心数据模型

- [x] `P0` 创建 `project` 表。
- [x] `P0` 创建 `input_source` 表。
- [x] `P0` 创建 `db_object` 表。
- [x] `P0` 创建 `parse_issue` 表。
- [x] `P0` 创建 `risk_issue` 表。
- [x] `P0` 创建 `conversion_result` 表。
- [x] `P0` 创建 `precheck_report` 表。
- [x] `P0` 创建 `review_record` 表。
- [x] `P0` 创建 `audit_log` 表。
- [x] `P0` 创建 `sql_version` 或等价 SQL 版本表。
- [x] `P0` 创建 `artifact_version` 或等价产物版本表。
- [x] `P0` 创建 `work_item` 或等价问题回流表。
- [x] `P0` 创建 `ai_provider_config` 表。
- [x] `P0` 创建 `prompt_template` 表。
- [x] `P0` 创建 `ai_suggestion` 表。
- [x] `P0` 创建 `ai_conversation` 表。
- [x] `P0` 创建 `ai_message` 表。
- [x] `P0` 创建 `agent_run` 表。
- [x] `P0` 创建 `agent_step` 表。
- [x] `P0` 创建 `skill_definition` 表。
- [x] `P0` 创建 `skill_run` 表。
- [x] `P0` 创建 `mcp_tool_call` 表。
- [x] `P0` 创建 `knowledge_document` 表。
- [x] `P0` 创建 `knowledge_chunk` 表。
- [x] `P0` 预留 pgvector `embedding` 字段。
- [x] `P0` 定义对象类型枚举。
- [x] `P0` 定义对象状态枚举。
- [x] `P0` 定义报告状态枚举：`DRAFT`、`READY_FOR_REVIEW`、`APPROVED`、`REJECTED`、`EXPIRED`。
- [x] `P0` 定义 SQL 基线状态枚举：`GENERATED`、`EDITED`、`REVIEWED`、`BASELINED`、`EXPIRED`。
- [x] `P0` 定义 Agent 状态枚举。
- [x] `P0` 定义 Skill 状态枚举。
- [x] `P0` 定义转换等级枚举：`AUTO`、`REVIEW_REQUIRED`、`DRAFT`、`MANUAL_REQUIRED`、`UNSUPPORTED`。
- [x] `P0` 定义风险等级枚举：`LOW`、`MEDIUM`、`HIGH`、`BLOCKER`。

## 5. P0：手工 SQL 垂直切片

- [x] `P0` 创建手工 SQL 输入 API。
- [x] `P0` 创建手工 SQL 输入页面。
- [x] `P0` 实现 SQL 原文保存。
- [x] `P0` 实现初版 statement splitter。
- [x] `P0` 识别 `CREATE TABLE`。
- [x] `P0` 识别 `CREATE INDEX`。
- [x] `P0` 识别 `CREATE VIEW`。
- [x] `P0` 识别 `CREATE SEQUENCE`。
- [x] `P0` 识别 `CREATE TRIGGER` 块。
- [x] `P0` 识别 `CREATE FUNCTION` 块。
- [x] `P0` 识别 `CREATE PROCEDURE` 块。
- [x] `P0` 识别 `CREATE PACKAGE` 和 `CREATE PACKAGE BODY`。
- [x] `P0` 解析失败时保存 `ParseIssue`。
- [x] `P0` 解析失败时保留原 SQL，不丢对象。

## 6. P0：转换引擎 MVP

- [x] `P0` 定义 `ObjectConverter` 接口。
- [x] `P0` 定义 `ConversionContext`。
- [x] `P0` 定义 `ConversionResult` 保存逻辑。
- [x] `P0` 实现 Oracle 类型到 PostgreSQL 类型映射。
- [x] `P0` 转换 `VARCHAR2`。
- [x] `P0` 转换 `NUMBER(p,s)`。
- [x] `P0` 转换无精度 `NUMBER` 并标风险。
- [x] `P0` 转换 `DATE` 并标语义风险。
- [x] `P0` 转换 `CLOB`。
- [x] `P0` 转换 `BLOB`。
- [x] `P0` 转换主键。
- [x] `P0` 转换唯一约束。
- [x] `P0` 转换 check 约束。
- [x] `P0` 转换普通索引。
- [x] `P0` 转换 sequence。
- [x] `P0` 转换简单 view。
- [x] `P0` 识别 trigger 并生成草稿或风险。
- [x] `P0` 识别 function/procedure 并生成草稿或风险。
- [x] `P0` 识别 package 并生成拆解建议。

## 7. P0：风险识别

- [x] `P0` 检测 `NUMBER` 精度风险。
- [x] `P0` 检测 Oracle `DATE` 语义风险。
- [x] `P0` 检测空字符串和 NULL 风险。
- [x] `P0` 检测 quoted identifier。
- [x] `P0` 检测 `ROWNUM`。
- [x] `P0` 检测 `CONNECT BY`。
- [x] `P0` 检测 `DECODE`。
- [x] `P0` 检测 `NVL` 并给出替换建议。
- [x] `P0` 检测 `SYSDATE` 并给出替换建议。
- [x] `P0` 检测 Oracle hint。
- [x] `P0` 检测 dynamic SQL。
- [x] `P0` 检测 autonomous transaction。
- [x] `P0` 检测 package global variable。
- [x] `P0` 计算对象风险等级。
- [x] `P0` 计算项目兼容性评分。

## 8. P0：AI 副驾驶 MVP

- [x] `P0` 定义 `AiProvider` 接口。
- [x] `P0` 实现 `MockAiProvider`，用于无 API key 的本地开发。
- [x] `P0` 预留云端模型 provider 配置。
- [x] `P0` 预留本地模型 provider 配置。
- [x] `P0` 将本地 LLM provider 标记为私有化部署优先路径。
- [x] `P1` 实现本地 OpenAI-compatible LLM provider，并保留 mock fallback。
- [x] `P0` 创建提示词模板版本机制。
- [x] `P0` 实现 AI 上下文构建器。
- [x] `P0` 实现敏感信息脱敏。
- [x] `P0` 实现风险解释能力。
- [x] `P0` 实现 SQL 改造建议能力。
- [x] `P0` 实现 PL/SQL 草稿说明能力。
- [x] `P0` 实现预处理报告摘要能力。
- [x] `P0` 定义知识库 chunk 模型。
- [x] `P0` 定义知识库 metadata 规范。
- [x] `P0` 实现知识入库前脱敏。
- [x] `P0` 实现轻量知识检索接口。
- [x] `P0` AI 回答保存引用的知识 chunk。
- [x] `P0` 保存 AI 请求模型名、提示词版本、输入 hash。
- [x] `P0` 保存 AI 输出为 `ai_suggestion`。
- [x] `P0` 支持用户接受 AI 建议。
- [x] `P0` 支持用户忽略 AI 建议。
- [x] `P0` 支持用户编辑 AI 建议后应用。
- [x] `P0` AI 建议应用后写入审计日志。
- [x] `P0` 禁止 AI 输出自动覆盖执行基线。
- [x] `P0` 禁止 AI 自动审核通过。

## 9. P0：Agent / MCP / Skills MVP

- [x] `P0` 定义 `AgentRuntime` 接口。
- [x] `P0` 实现轻量 Agent 状态机。
- [x] `P0` 实现 `AssessmentAgent`。
- [x] `P0` 实现 `ConversionAgent`。
- [x] `P0` 实现 `ErrorDiagnosisAgent` 原型。
- [x] `P0` 记录 `agent_run`。
- [x] `P0` 记录 `agent_step`。
- [x] `P0` 定义 `SkillRegistry`。
- [x] `P0` 定义 `SkillExecutor`。
- [x] `P0` 定义 `skill.yaml` 格式。
- [x] `P0` 实现 `oracle-table-ddl` Skill。
- [x] `P0` 实现 `oracle-view-sql` Skill。
- [x] `P0` 实现 `oracle-trigger-to-postgres` Skill 草稿。
- [x] `P0` Skill 输出必须做 schema validation。
- [x] `P0` Skill 必须声明允许调用的工具。
- [x] `P0` Skill 必须声明是否需要审核。
- [x] `P0` 定义 `ToolRegistry`。
- [x] `P0` 定义 `McpGateway`。
- [x] `P0` 暴露只读 MCP resource 原型。
- [x] `P0` 暴露安全 MCP tool 原型。
- [x] `P0` 暴露 MCP prompt 原型。
- [x] `P0` MCP tool 调用必须 allowlist。
- [x] `P0` MCP tool 调用必须记录审计。
- [x] `P0` MCP 写操作默认 dry-run。
- [x] `P0` 生产环境默认关闭外部 MCP Client。

## 10. P0：知识库 MVP

- [x] `P0` 定义规则知识库内容格式。
- [x] `P0` 定义案例知识库内容格式。
- [x] `P0` 定义文档知识库内容格式。
- [x] `P0` 定义项目知识库内容格式。
- [x] `P0` 内置第一批 Oracle -> PostgreSQL 风险解释知识。
- [x] `P0` 内置第一批类型映射知识。
- [x] `P0` 内置第一批函数映射知识。
- [x] `P0` 实现知识 chunk 版本管理。
- [x] `P0` 实现知识 chunk 来源记录。
- [x] `P0` 实现知识 chunk 审核状态。
- [x] `P0` 验证敏感信息不会进入知识库。

## 11. P0：转换工作台

- [x] `P0` 创建对象清单页面。
- [x] `P0` 支持按对象类型筛选。
- [x] `P0` 支持按风险等级筛选。
- [x] `P0` 创建转换工作台页面。
- [x] `P0` 集成 Monaco Editor。
- [x] `P0` 左侧显示 Oracle 原始 SQL。
- [x] `P0` 右侧显示 PostgreSQL 目标 SQL。
- [x] `P0` 显示转换等级。
- [x] `P0` 显示风险问题。
- [x] `P0` 支持用户编辑目标 SQL。
- [x] `P0` 保存用户编辑版本。
- [x] `P0` 记录目标 SQL 版本来源：规则生成、AI 建议、人工编辑。
- [x] `P0` 实现 SQL 基线冻结逻辑。
- [x] `P0` 审核通过后生成 `baseline_sql`。
- [x] `P0` 修改目标 SQL 后标记旧报告和旧基线过期。
- [x] `P0` 支持恢复自动生成版本。
- [x] `P0` 记录编辑审计日志。
- [x] `P0` 添加“AI 解释风险”按钮。
- [x] `P0` 添加“AI 改造建议”按钮。
- [x] `P0` 添加 AI 建议接受/忽略入口。

## 12. P0：SQL 文件导入

- [x] `P0` 创建文件上传 API。
- [x] `P0` 创建文件上传页面。
- [x] `P0` 保存文件 checksum。
- [x] `P0` 记录文件编码。
- [x] `P0` 异步解析文件。
- [x] `P0` 推送解析进度。
- [x] `P0` 支持 Data Pump SQLFILE 文本导入。
- [x] `P0` 对无法执行的匿名 PL/SQL 块标风险。
- [x] `P0` 上传后生成对象清单。

## 13. P0：预处理报告

- [x] `P0` 生成资产统计。
- [x] `P0` 生成对象类型分布。
- [x] `P0` 生成风险分布。
- [x] `P0` 生成高风险对象列表。
- [x] `P0` 生成类型映射清单。
- [x] `P0` 生成 SQL/PLSQL 问题清单。
- [x] `P0` 生成建议处理方式。
- [x] `P0` 生成建议迁移顺序草案。
- [x] `P0` 生成 AI 管理层摘要。
- [x] `P0` 生成 AI 开发改造摘要。
- [x] `P0` 生成报告快照。
- [x] `P0` 报告快照记录对象版本、转换结果版本、风险版本、AI 建议版本。
- [x] `P0` 修改对象后标记报告过期。
- [x] `P0` 修改目标 SQL 后标记报告过期。
- [x] `P0` 修改规则配置后标记报告过期。
- [x] `P0` 创建预处理报告页面。

## 14. P0：审核流程

- [x] `P0` 提交报告审核。
- [x] `P0` 审核通过。
- [x] `P0` 有条件通过。
- [x] `P0` 驳回。
- [x] `P0` 请求修改。
- [x] `P0` 保存审核意见。
- [x] `P0` 保存审核时间和审核人。
- [x] `P0` 审核记录绑定报告版本和 SQL 版本。
- [x] `P0` 未审核通过时禁止正式导出。
- [x] `P0` 报告过期时禁止正式导出。
- [x] `P0` 审核通过后冻结 SQL 基线。
- [x] `P0` 创建审核记录页面。

## 15. P0：SQL 包导出

- [x] `P0` 生成 PostgreSQL SQL 包。
- [x] `P0` 按对象类型排序 SQL。
- [x] `P0` 只使用审核冻结后的 `baseline_sql`。
- [x] `P0` 导出前检查报告和审核状态。
- [x] `P0` 导出前检查报告是否过期。
- [x] `P0` 导出前检查 SQL 基线是否过期。
- [x] `P0` 导出 zip 或单个 `.sql` 文件。
- [x] `P0` 记录导出审计日志。

## 16. P0：闭环回归

- [x] `P0` 准备 `CREATE TABLE users` 端到端样例。
- [x] `P0` 准备包含 view/index/sequence 的 SQL 文件样例。
- [x] `P0` 准备包含 trigger/function/package 的风险样例。
- [x] `P0` 编写 P0 手工验收清单。
- [x] `P0` 验证手工 SQL 到 SQL 包导出完整闭环。
- [x] `P0` 验证 SQL 文件到 SQL 包导出完整闭环。
- [x] `P0` 验证解析失败不丢失原文。
- [x] `P0` 验证 AI 建议不能自动覆盖目标 SQL。
- [x] `P0` 验证未审核不能导出正式 SQL 包。
- [x] `P0` 验证报告过期不能导出正式 SQL 包。
- [x] `P0` 验证修改目标 SQL 后报告过期。
- [x] `P0` 验证审核通过后生成 SQL 基线。
- [x] `P0` 验证关键动作都有审计日志。

## 17. P1：数据源管理

- [x] `P1` 创建 `datasource_config` 表。
- [x] `P1` 加密保存数据库密码。
- [x] `P1` Oracle 数据源表单。
- [x] `P1` PostgreSQL 数据源表单。
- [x] `P1` Oracle 连接测试。
- [x] `P1` PostgreSQL 连接测试。
- [x] `P1` Oracle 版本识别。
- [x] `P1` PostgreSQL 版本识别。
- [x] `P1` 权限检查。
- [x] `P1` 密码脱敏。

## 18. P1：Oracle 直连扫描

- [x] `P1` 扫描 schema。
- [x] `P1` 扫描 table。
- [x] `P1` 扫描 column。
- [x] `P1` 扫描 primary key。
- [x] `P1` 扫描 foreign key。
- [x] `P1` 扫描 unique/check constraint。
- [x] `P1` 扫描 index。
- [x] `P1` 扫描 sequence。
- [x] `P1` 扫描 view。
- [x] `P1` 扫描 trigger。
- [x] `P1` 扫描 function。
- [x] `P1` 扫描 procedure。
- [x] `P1` 扫描 package。
- [x] `P1` 扫描 synonym。
- [x] `P1` 扫描 comment。
- [x] `P1` 扫描 partition metadata。
- [x] `P1` 使用 `DBMS_METADATA` 获取 DDL。
- [x] `P1` 使用源码视图获取 PL/SQL。
- [x] `P1` 扫描进度实时推送。
- [x] `P1` 权限不足时记录风险。

## 19. P1：迁移计划和 DDL 执行

- [x] `P1` 创建 `migration_plan` 表。
- [x] `P1` 创建 `migration_plan_step` 表。
- [x] `P1` 迁移计划必须绑定已审核 SQL 基线版本。
- [x] `P1` SQL 基线过期时禁止生成正式迁移计划。
- [x] `P1` 生成 schema 创建步骤。
- [x] `P1` 生成 table 创建步骤。
- [x] `P1` 生成 index 创建步骤。
- [x] `P1` 生成 constraint 创建步骤。
- [x] `P1` 生成 view 创建步骤。
- [x] `P1` 生成 routine 创建步骤。
- [x] `P1` 依赖排序。
- [x] `P1` 创建 PostgreSQL DDL 执行器。
- [x] `P1` 执行日志。
- [x] `P1` 单步重试。
- [x] `P1` 失败原因展示。
- [x] `P1` DDL 执行失败时生成待处理问题。

## 20. P1：基础数据迁移

- [x] `P1` 实现 Oracle streaming reader。
- [x] `P1` 实现 PostgreSQL COPY writer。
- [x] `P1` 定义 `MemoryBudgetManager`。
- [x] `P1` 定义项目级堆外内存预算。
- [x] `P1` 定义 task 级堆外内存预算。
- [x] `P1` 定义 shard 级 `Arena` 生命周期。
- [x] `P1` 实现 FFM COPY 编码缓冲。
- [x] `P1` 实现 FFM buffer flush 策略。
- [x] `P1` 实现 arena close 安全检查。
- [x] `P1` 定义 NULL 和空字符串编码策略。
- [x] `P1` 支持小表全量迁移。
- [x] `P1` 记录迁移行数。
- [x] `P1` 记录 rows/s。
- [x] `P1` 实时推送进度。
- [x] `P1` 记录堆外内存使用量。
- [x] `P1` 记录 arena 未关闭数量。
- [x] `P1` 堆外内存超限时触发限流。
- [x] `P1` 捕获 COPY 错误。
- [x] `P1` 失败重试。
- [x] `P1` 数据迁移失败时生成待处理问题。
- [x] `P1` 行数校验失败时生成待处理问题。

## 21. P1：执行校验闭环回归

- [x] `P1` 准备测试 Oracle 源表和 PostgreSQL 目标库。
- [x] `P1` 验证已审核 SQL 基线可以生成迁移计划。
- [x] `P1` 验证未审核 SQL 不能生成正式迁移计划。
- [x] `P1` 验证 DDL 能执行到 PostgreSQL。
- [x] `P1` 验证小表 COPY 后行数一致。
- [x] `P1` 验证 DDL 错误能定位到对象和 SQL。
- [x] `P1` 验证 COPY 错误能定位到表和批次。
- [x] `P1` 验证校验失败能回流为待处理问题。
- [x] `P1` 验证 COPY 迁移时 heap 占用稳定。
- [x] `P1` 验证 shard 完成后 FFM arena 释放。

## 22. P2：高速数据迁移

- [x] `P2` 大表识别。
- [x] `P2` 主键 range 分片。
- [x] `P2` hash 分片 fallback。
- [x] `P2` shard checkpoint。
- [x] `P2` 断点续传。
- [x] `P2` shard 级重试。
- [x] `P2` 项目级并发限制。
- [x] `P2` 表级并发限制。
- [x] `P2` 全局并发限制。
- [x] `P2` LOB 迁移优化。
- [x] `P2` LOB 分块读取使用 FFM 缓冲。
- [x] `P2` checksum 使用 FFM 分片缓冲。
- [x] `P2` 迁移限速。
- [x] `P2` 暂停任务。
- [x] `P2` 取消任务。
- [x] `P2` 恢复任务。

## 23. P2：校验

- [x] `P2` 对象存在校验。
- [x] `P2` 行数校验。
- [x] `P2` 抽样校验。
- [x] `P2` 分片 checksum。
- [x] `P2` view 执行校验。
- [x] `P2` routine 编译校验。
- [x] `P2` 创建校验报告。
- [x] `P2` 校验失败详情。

## 24. P2：PL/SQL 增强

- [x] `P2` trigger 转换增强。
- [x] `P2` function 转换增强。
- [x] `P2` procedure 转换增强。
- [x] `P2` package spec 分析。
- [x] `P2` package body 分析。
- [x] `P2` package routine 拆解。
- [x] `P2` Oracle 内置包替代建议。
- [x] `P2` dynamic SQL 标注增强。
- [x] `P2` exception 语义差异提示。

## 25. P2：AI 增强

- [x] `P2` AI 执行错误诊断。
- [x] `P2` AI 校验差异排查建议。
- [x] `P2` AI 规则沉淀建议。
- [x] `P2` AI 基于历史项目推荐规则模板。
- [x] `P2` AI 项目级自然语言问答。
- [x] `P2` AI 长 PL/SQL 分块摘要。
- [x] `P2` AI package 改造方案生成。
- [x] `P2` AI 成本统计和用量看板。
- [x] `P2` RAG 多路召回。
- [x] `P2` RAG rerank。
- [x] `P2` 历史案例脱敏后进入全局知识库。
- [x] `P2` 本地 embedding 模型适配。
- [x] `P2` 知识库命中率和采纳率看板。

## 26. P2：规则沉淀闭环

- [x] `P2` 从人工编辑 SQL 中抽取规则候选。
- [x] `P2` 从 AI 建议中抽取规则候选。
- [x] `P2` 规则候选必须人工确认。
- [x] `P2` 规则候选进入测试样例。
- [x] `P2` 规则通过测试后才能启用。
- [x] `P2` 规则启用后可重新转换同类 SQL。
- [x] `P2` 记录规则来源项目和审核人。

## 27. 技术预研任务

- [x] `P0` 验证 statement splitter 能处理 trigger/function/package。
- [x] `P0` 准备 SQL/PLSQL fixture 样例集。
- [x] `P0` 验证 AI Provider 抽象。
- [x] `P0` 验证 AI 提示词脱敏。
- [x] `P0` 验证 AI 建议保存和审计。
- [x] `P0` 验证 pgvector 扩展可用性。
- [x] `P0` 验证 Spring AI PgVectorStore。
- [x] `P0` 验证知识 chunk metadata filter。
- [x] `P0` 验证 embedding 前脱敏。
- [x] `P0` 验证 Spring AI MCP Java SDK。
- [x] `P0` 验证 MCP tool allowlist 和超时。
- [x] `P0` 验证 Skill YAML 加载和 schema validation。
- [x] `P0` 验证 Agent 状态机暂停、失败和审计。
- [x] `P1` 验证 Oracle `DBMS_METADATA` 权限和输出。
- [x] `P1` 验证 Oracle `ALL_SOURCE` 可访问性。
- [x] `P1` 验证 pgJDBC CopyManager 写入。
- [x] `P1` 验证 Java 25 虚拟线程和 JDBC 连接池配合。
- [x] `P1` 验证 Java 25 FFM `MemorySegment` 和 `Arena` 生命周期。
- [x] `P1` 验证 FFM COPY buffer 与 heap buffer 吞吐和 GC 差异。
- [x] `P1` 验证堆外内存预算耗尽时的限流行为。
- [x] `P2` 验证大表分片策略。
- [x] `P2` 验证 checksum 成本。

## 28. P3：生产硬化 Backlog

- [x] `P3` 增加 pgvector-backed `KnowledgeRepository` 或 Spring AI PgVectorStore adapter。
- [x] `P3` 本地 LLM 增加健康检查、模型发现、超时指标和 fallback 计数。
- [x] `P3` 前端显示本地 LLM endpoint 健康状态、当前模型、知识库命中率和云端 provider 开关状态。
- [x] `P3` 生产 profile 禁止使用默认元数据库密码和默认 datasource 加密 key。
- [x] `P3` 动态 SQL 中的 table、schema、column 标识符统一走白名单或 identifier validator。
- [x] `P3` DML/INSERT 文件导入设计专用闭环：入口、风险、产物、门禁、失败回流和验收用例。
- [x] `P3` 增加真实 Oracle/PostgreSQL/pgvector 环境的集成测试或 CI profile。

## 29. Definition of Done

一个任务完成必须满足：

- 有接口或页面可验证。
- 有失败场景处理。
- 有必要日志。
- 有基础测试或 fixture。
- 有产物版本记录。
- 有门禁验证。
- 失败能回流为待处理问题。
- 不泄露密码。
- AI 提示词不包含密码、连接串、密钥。
- embedding 内容不包含密码、连接串、密钥。
- AI 回答能追溯知识来源。
- AI 输出不会自动执行或自动通过审核。
- Agent 不能绕过工具权限和审核门禁。
- MCP tool 调用有 allowlist、超时、审计。
- Skill 有版本、fixture、输出 schema。
- 不绕过审核门禁。
- 不丢失原始 SQL。

## 30. MVP 完成标准

MVP 完成时，用户应该可以：

- 创建迁移项目。
- 输入或上传 Oracle SQL。
- 查看识别出的对象清单。
- 查看 Oracle SQL 到 PostgreSQL SQL 的转换结果。
- 手工修改目标 SQL。
- 查看风险清单。
- 使用 AI 解释风险并生成改造建议。
- 使用内置 Skill 生成转换结果或草稿。
- Agent 运行过程可审计。
- MCP tool/resource/prompt 原型可用且默认安全。
- 生成预处理报告。
- 提交审核并通过。
- 导出 PostgreSQL SQL 包。
- 修改目标 SQL 后能看到报告过期。
- 审核通过后能看到冻结 SQL 基线。
- 未审核或报告过期时不能导出正式 SQL 包。

这才是第一版真正可用的产品闭环。


