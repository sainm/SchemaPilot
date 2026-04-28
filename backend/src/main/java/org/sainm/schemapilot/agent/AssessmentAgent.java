package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.sainm.schemapilot.model.AgentStatus;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;
import org.sainm.schemapilot.sql.AnalyzedStatement;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class AssessmentAgent implements MigrationAgent {
    private final ManualSqlAnalysisService analysisService;
    private final KnowledgeService knowledgeService;

    AssessmentAgent(ManualSqlAnalysisService analysisService, KnowledgeService knowledgeService) {
        this.analysisService = analysisService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public AgentType type() {
        return AgentType.ASSESSMENT;
    }

    @Override
    public Map<String, Object> execute(AgentRunRequest request, AgentStepRecorder recorder) {
        requireSql(request.sql());
        recorder.step(AgentStatus.WAITING_FOR_TOOL, "Analyze Oracle SQL", "sql.analyze", sizeSummary(request.sql()), "Classifier, converter and risk engine will run locally.");
        var analysis = analysisService.analyze(request.sql());
        var highestRisk = analysis.statements().stream()
                .map(AnalyzedStatement::riskLevel)
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse(RiskLevel.LOW);
        var objectDistribution = new EnumMap<ObjectType, Long>(ObjectType.class);
        analysis.statements().stream()
                .collect(Collectors.groupingBy(AnalyzedStatement::objectType, () -> new EnumMap<>(ObjectType.class), Collectors.counting()))
                .forEach(objectDistribution::put);
        recorder.step(AgentStatus.WAITING_FOR_TOOL, "Search project knowledge", "knowledge.search", highestRisk.name(), "Knowledge chunks will be cited as review context.");
        var knowledge = knowledgeService.search(new KnowledgeSearchRequest(highestRisk.name(), Map.of(), 3));
        recorder.step(AgentStatus.PRODUCING, "Produce precheck recommendation", null, "score=" + analysis.compatibilityScore(), "No SQL baseline is changed by this agent.");
        return Map.of(
                "statementCount", analysis.statementCount(),
                "riskCount", analysis.riskCount(),
                "compatibilityScore", analysis.compatibilityScore(),
                "highestRisk", highestRisk.name(),
                "objectDistribution", objectDistribution,
                "knowledgeChunkKeys", knowledge.stream().map(result -> result.chunk().key()).toList(),
                "recommendedGate", highestRisk == RiskLevel.HIGH || highestRisk == RiskLevel.BLOCKER ? "REVIEW_REQUIRED" : "READY_FOR_REVIEW"
        );
    }

    private void requireSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BadRequestException("Agent run requires sql.");
        }
    }

    private int riskWeight(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case BLOCKER -> 4;
        };
    }

    private String sizeSummary(String value) {
        return value == null ? "empty" : value.length() + " chars";
    }
}
