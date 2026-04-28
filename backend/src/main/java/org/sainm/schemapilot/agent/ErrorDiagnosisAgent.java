package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.sainm.schemapilot.model.AgentStatus;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class ErrorDiagnosisAgent implements MigrationAgent {
    private final KnowledgeService knowledgeService;
    private final ManualSqlAnalysisService analysisService;

    ErrorDiagnosisAgent(KnowledgeService knowledgeService, ManualSqlAnalysisService analysisService) {
        this.knowledgeService = knowledgeService;
        this.analysisService = analysisService;
    }

    @Override
    public AgentType type() {
        return AgentType.ERROR_DIAGNOSIS;
    }

    @Override
    public Map<String, Object> execute(AgentRunRequest request, AgentStepRecorder recorder) {
        if (request.errorMessage() == null || request.errorMessage().isBlank()) {
            throw new BadRequestException("Error diagnosis requires errorMessage.");
        }
        recorder.step(AgentStatus.WAITING_FOR_TOOL, "Search error knowledge", "knowledge.search", request.errorMessage(), "Search is local and redacted.");
        var knowledge = knowledgeService.search(new KnowledgeSearchRequest(request.errorMessage(), Map.of(), 3));
        var sqlPresent = request.sql() != null && !request.sql().isBlank();
        if (sqlPresent) {
            recorder.step(AgentStatus.WAITING_FOR_TOOL, "Analyze failing SQL", "sql.analyze", sizeSummary(request.sql()), "Original SQL is preserved for review.");
            analysisService.analyze(request.sql());
        }
        recorder.step(AgentStatus.PRODUCING, "Produce diagnosis", null, "knowledgeHits=" + knowledge.size(), "Diagnosis becomes a work item candidate, not an automatic fix.");
        return Map.of(
                "category", categorizeError(request.errorMessage()),
                "knowledgeChunkKeys", knowledge.stream().map(result -> result.chunk().key()).toList(),
                "recommendedAction", "Create a review work item with the failing SQL, error text, and cited knowledge chunks.",
                "sqlAnalyzed", sqlPresent
        );
    }

    private String categorizeError(String message) {
        var lowered = message.toLowerCase();
        if (lowered.contains("syntax")) {
            return "SYNTAX";
        }
        if (lowered.contains("permission") || lowered.contains("privilege")) {
            return "PERMISSION";
        }
        if (lowered.contains("type") || lowered.contains("cast")) {
            return "TYPE_MAPPING";
        }
        return "GENERAL";
    }

    private String sizeSummary(String value) {
        return value == null ? "empty" : value.length() + " chars";
    }
}
