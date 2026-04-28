package org.sainm.schemapilot.agent;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.model.AgentStatus;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.skill.SkillExecutor;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class ConversionAgent implements MigrationAgent {
    private final ManualSqlAnalysisService analysisService;
    private final SkillExecutor skillExecutor;

    ConversionAgent(ManualSqlAnalysisService analysisService, SkillExecutor skillExecutor) {
        this.analysisService = analysisService;
        this.skillExecutor = skillExecutor;
    }

    @Override
    public AgentType type() {
        return AgentType.CONVERSION;
    }

    @Override
    public Map<String, Object> execute(AgentRunRequest request, AgentStepRecorder recorder) {
        requireSql(request.sql());
        recorder.step(AgentStatus.WAITING_FOR_TOOL, "Classify conversion skill", "sql.analyze", sizeSummary(request.sql()), "First statement chooses a built-in skill.");
        var analysis = analysisService.analyze(request.sql());
        if (analysis.statements().isEmpty()) {
            throw new BadRequestException("No statement to convert.");
        }
        var skillId = skillFor(analysis.statements().getFirst().objectType());
        recorder.step(AgentStatus.WAITING_FOR_TOOL, "Run conversion skill", "skill.run", skillId, "Skill output must pass schema validation.");
        var skillRun = skillExecutor.execute(skillId, request.sql());
        recorder.step(AgentStatus.PRODUCING, "Produce conversion draft", null, skillRun.id().toString(), "Human review is still required before export.");
        return Map.of(
                "skillRunId", skillRun.id(),
                "skillId", skillRun.skillId(),
                "skillVersion", skillRun.skillVersion(),
                "valid", skillRun.valid(),
                "requiresReview", true,
                "output", skillRun.output()
        );
    }

    private String skillFor(ObjectType objectType) {
        return switch (objectType) {
            case TABLE -> "oracle-table-ddl";
            case VIEW -> "oracle-view-sql";
            case TRIGGER -> "oracle-trigger-to-postgres";
            default -> "oracle-table-ddl";
        };
    }

    private void requireSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BadRequestException("Agent run requires sql.");
        }
    }

    private String sizeSummary(String value) {
        return value == null ? "empty" : value.length() + " chars";
    }
}
