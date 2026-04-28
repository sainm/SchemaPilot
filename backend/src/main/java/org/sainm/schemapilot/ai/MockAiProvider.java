package org.sainm.schemapilot.ai;

import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class MockAiProvider implements AiProvider {
    private final AiContextBuilder contextBuilder;
    private final KnowledgeService knowledgeService;
    private final AiGovernanceRegistry governanceRegistry;

    MockAiProvider(AiContextBuilder contextBuilder, KnowledgeService knowledgeService, AiGovernanceRegistry governanceRegistry) {
        this.contextBuilder = contextBuilder;
        this.knowledgeService = knowledgeService;
        this.governanceRegistry = governanceRegistry;
    }

    @Override
    public AiSuggestionDraft explainRisk(RiskExplanationRequest request) {
        var riskType = request.riskType().toUpperCase(Locale.ROOT);
        var suggestion = switch (riskType) {
            case "NUMBER_PRECISION" -> "确认源字段真实取值范围。若全为整数且范围较小，可改为 integer 或 bigint；否则保留 numeric 并补充精度。";
            case "DATE_SEMANTICS" -> "Oracle DATE 同时包含日期和时间。默认迁移为 PostgreSQL timestamp，再由业务确认是否需要 date。";
            case "CURRENT_TIME" -> "将 SYSDATE/SYSTIMESTAMP 映射为 CURRENT_TIMESTAMP，并在审核时确认时区和事务时间语义。";
            case "ROWNUM" -> "ROWNUM 需要按场景改写为 LIMIT/OFFSET、row_number() 窗口函数或 CTE。";
            case "CONNECT_BY" -> "CONNECT BY 需要改写为 WITH RECURSIVE，并确认层级排序和循环检测策略。";
            case "TRIGGER_BODY" -> "Oracle trigger 需要拆成 PostgreSQL trigger function 和 trigger binding，重点检查 :NEW/:OLD 赋值。";
            case "PACKAGE" -> "Oracle package 应拆解为函数、过程和表结构或配置项；全局变量和初始化逻辑需要人工设计替代方案。";
            case "NVL" -> "NVL 已可替换为 COALESCE，但要确认返回类型推断和参数副作用。";
            case "EMPTY_STRING_NULL" -> "Oracle 空字符串等同 NULL，PostgreSQL 会保留空字符串。需要重写比较、默认值和唯一性判断。";
            case "DYNAMIC_SQL" -> "EXECUTE IMMEDIATE 需要先还原运行时拼接 SQL，再按对象或语句逐条改造。";
            case "AUTONOMOUS_TRANSACTION" -> "自治事务需要重新设计事务边界，通常移到应用层、任务队列或独立连接执行。";
            default -> "该风险需要人工审核。请结合原始 SQL、目标 PostgreSQL 版本和业务语义确认最终改造方式。";
        };

        var chunks = knowledgeService.search(new KnowledgeSearchRequest(riskType, Map.of("riskType", riskType), 3));
        return draft("risk-explanation", suggestion, List.of("riskType=" + riskType, contextBuilder.riskContext(request)), chunks.stream().map(result -> result.chunk().key()).toList());
    }

    @Override
    public AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request) {
        var suggestion = "建议先接受规则生成的 PostgreSQL SQL 作为草稿，然后逐项处理风险："
                + String.join(", ", request.riskTypes())
                + "。对 ROWNUM 改写 LIMIT/window，对 NVL/COALESCE 校验类型，对 PL/SQL 保持人工审核。";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.riskTypes()), Map.of(), 3));
        return draft("sql-rewrite", suggestion, List.of(contextBuilder.sqlRewriteContext(request)), chunks.stream().map(result -> result.chunk().key()).toList());
    }

    @Override
    public AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request) {
        var suggestion = "该 " + request.objectType()
                + " 需要按 PostgreSQL PL/pgSQL 重新设计。先识别参数、返回值、变量、异常处理和事务语义，再生成函数或过程草稿。";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(request.objectType(), Map.of(), 3));
        return draft("plsql-explanation", suggestion, List.of(contextBuilder.plsqlContext(request)), chunks.stream().map(result -> result.chunk().key()).toList());
    }

    @Override
    public AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request) {
        var suggestion = "预处理结果包含 " + request.statementCount() + " 条语句、" + request.riskCount()
                + " 个风险，其中高风险 " + request.highRiskCount() + " 个、阻断 " + request.blockerCount()
                + " 个。建议先处理阻断项，再冻结可自动转换对象。";
        var chunks = knowledgeService.search(new KnowledgeSearchRequest(String.join(" ", request.topRiskTypes()), Map.of(), 3));
        return draft("precheck-summary", suggestion, List.of(contextBuilder.precheckContext(request)), chunks.stream().map(result -> result.chunk().key()).toList());
    }

    private AiSuggestionDraft draft(String promptKey, String suggestion, List<String> evidence, List<String> citedChunkKeys) {
        return new AiSuggestionDraft(
                "mock",
                "schemapilot-rule-backed",
                governanceRegistry.activePromptVersion(promptKey),
                suggestion,
                evidence,
                citedChunkKeys
        );
    }
}
