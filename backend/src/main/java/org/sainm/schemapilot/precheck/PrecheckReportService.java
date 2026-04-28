package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.ai.AiProvider;
import org.sainm.schemapilot.ai.PrecheckSummaryRequest;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;
import org.sainm.schemapilot.sql.AnalyzedStatement;
import org.sainm.schemapilot.sql.DetectedRisk;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PrecheckReportService {
    private static final List<TypeRule> TYPE_RULES = List.of(
            new TypeRule(Pattern.compile("(?i)\\bVARCHAR2\\b|\\bNVARCHAR2\\b"), "VARCHAR2/NVARCHAR2", "varchar"),
            new TypeRule(Pattern.compile("(?i)\\bNUMBER\\s*\\("), "NUMBER(p,s)", "integer/bigint/numeric"),
            new TypeRule(Pattern.compile("(?i)\\bNUMBER\\b(?!\\s*\\()"), "NUMBER", "numeric"),
            new TypeRule(Pattern.compile("(?i)\\bDATE\\b"), "DATE", "timestamp"),
            new TypeRule(Pattern.compile("(?i)\\bCLOB\\b"), "CLOB", "text"),
            new TypeRule(Pattern.compile("(?i)\\bBLOB\\b|\\bRAW\\b"), "BLOB/RAW", "bytea")
    );

    private final ManualSqlAnalysisService analysisService;
    private final AiProvider aiProvider;

    public PrecheckReportService(ManualSqlAnalysisService analysisService, AiProvider aiProvider) {
        this.analysisService = analysisService;
        this.aiProvider = aiProvider;
    }

    public PrecheckReportResponse generate(String sql) {
        var analysis = analysisService.analyze(sql);
        var statements = analysis.statements();
        var riskDistribution = riskDistribution(statements);
        var topRiskTypes = topRiskTypes(statements);
        var aiSummary = aiProvider.summarizePrecheck(new PrecheckSummaryRequest(
                analysis.statementCount(),
                analysis.riskCount(),
                Math.toIntExact(riskDistribution.getOrDefault(RiskLevel.HIGH, 0L)),
                Math.toIntExact(riskDistribution.getOrDefault(RiskLevel.BLOCKER, 0L)),
                topRiskTypes
        ));

        return new PrecheckReportResponse(
                "precheck-" + UUID.randomUUID(),
                Instant.now(),
                analysis,
                objectTypeDistribution(statements),
                riskDistribution,
                highRiskObjects(statements),
                typeMappings(statements),
                issues(statements),
                handlingRecommendations(topRiskTypes),
                migrationOrderDraft(statements),
                aiSummary.suggestion(),
                developerSummary(statements, topRiskTypes),
                versionRefs(analysis, aiSummary)
        );
    }

    private Map<String, String> versionRefs(org.sainm.schemapilot.sql.SqlAnalysisResponse analysis, org.sainm.schemapilot.ai.AiSuggestionDraft aiSummary) {
        return Map.of(
                "objectVersion", "object-count-" + analysis.statementCount(),
                "conversionVersion", "rule-converter-v1",
                "riskVersion", "risk-engine-v1-" + analysis.riskCount(),
                "aiSuggestionVersion", aiSummary.promptVersion()
        );
    }

    private Map<ObjectType, Long> objectTypeDistribution(List<AnalyzedStatement> statements) {
        return statements.stream()
                .collect(Collectors.groupingBy(
                        AnalyzedStatement::objectType,
                        () -> new EnumMap<>(ObjectType.class),
                        Collectors.counting()
                ));
    }

    private Map<RiskLevel, Long> riskDistribution(List<AnalyzedStatement> statements) {
        return statements.stream()
                .flatMap(statement -> statement.risks().stream())
                .collect(Collectors.groupingBy(
                        DetectedRisk::level,
                        () -> new EnumMap<>(RiskLevel.class),
                        Collectors.counting()
                ));
    }

    private List<HighRiskObject> highRiskObjects(List<AnalyzedStatement> statements) {
        return statements.stream()
                .filter(statement -> statement.riskLevel() == RiskLevel.HIGH || statement.riskLevel() == RiskLevel.BLOCKER)
                .map(statement -> new HighRiskObject(
                        statement.index(),
                        statement.objectType(),
                        statement.objectName(),
                        statement.riskLevel(),
                        statement.risks().stream().map(DetectedRisk::type).distinct().toList()
                ))
                .toList();
    }

    private List<TypeMappingItem> typeMappings(List<AnalyzedStatement> statements) {
        var mappings = new ArrayList<TypeMappingItem>();
        for (var statement : statements) {
            for (var rule : TYPE_RULES) {
                if (rule.pattern().matcher(statement.originalSql()).find()) {
                    mappings.add(new TypeMappingItem(statement.index(), statement.objectName(), rule.sourceType(), rule.targetType()));
                }
            }
        }
        return List.copyOf(mappings);
    }

    private List<SqlIssueItem> issues(List<AnalyzedStatement> statements) {
        return statements.stream()
                .flatMap(statement -> statement.risks().stream()
                        .map(risk -> new SqlIssueItem(
                                statement.index(),
                                statement.objectName(),
                                risk.type(),
                                risk.level(),
                                risk.message(),
                                risk.suggestion()
                        )))
                .toList();
    }

    private List<String> handlingRecommendations(List<String> topRiskTypes) {
        if (topRiskTypes.isEmpty()) {
            return List.of("当前规则未发现高信号风险，可以进入人工抽样审核。");
        }
        var recommendations = new ArrayList<String>();
        for (var riskType : topRiskTypes) {
            recommendations.add(switch (riskType) {
                case "PACKAGE", "PACKAGE_GLOBAL_STATE" -> "先拆解 package 和全局状态，再评估函数/过程迁移边界。";
                case "TRIGGER_BODY" -> "将 trigger 转为 PostgreSQL trigger function 草稿，并人工审核 :NEW/:OLD 语义。";
                case "ROWNUM" -> "按分页、Top-N 或去重语义将 ROWNUM 改写为 LIMIT 或 window function。";
                case "CONNECT_BY" -> "将层级查询改写为 WITH RECURSIVE，并补充循环检测测试。";
                case "AUTONOMOUS_TRANSACTION" -> "自治事务需要重新设计事务边界，不能直接自动转换。";
                default -> "处理 " + riskType + " 风险并补充对象级审核记录。";
            });
        }
        return recommendations;
    }

    private List<String> migrationOrderDraft(List<AnalyzedStatement> statements) {
        return statements.stream()
                .sorted(Comparator.comparingInt(statement -> orderWeight(statement.objectType())))
                .map(statement -> statement.objectType() + " " + statement.objectName())
                .distinct()
                .toList();
    }

    private int orderWeight(ObjectType type) {
        return switch (type) {
            case SEQUENCE -> 1;
            case TABLE -> 2;
            case INDEX -> 3;
            case VIEW -> 4;
            case FUNCTION, PROCEDURE, TRIGGER -> 5;
            case PACKAGE, PACKAGE_BODY -> 9;
            default -> 8;
        };
    }

    private List<String> topRiskTypes(List<AnalyzedStatement> statements) {
        return statements.stream()
                .flatMap(statement -> statement.risks().stream())
                .map(DetectedRisk::type)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(8)
                .toList();
    }

    private String developerSummary(List<AnalyzedStatement> statements, List<String> topRiskTypes) {
        var manualObjects = statements.stream()
                .filter(statement -> statement.riskLevel() == RiskLevel.HIGH || statement.riskLevel() == RiskLevel.BLOCKER)
                .map(statement -> statement.objectType() + " " + statement.objectName())
                .toList();
        return "开发改造重点：" + (manualObjects.isEmpty() ? "暂无高风险对象" : String.join(", ", manualObjects))
                + "。优先风险：" + (topRiskTypes.isEmpty() ? "无" : String.join(", ", topRiskTypes)) + "。";
    }

    private record TypeRule(Pattern pattern, String sourceType, String targetType) {
    }
}
