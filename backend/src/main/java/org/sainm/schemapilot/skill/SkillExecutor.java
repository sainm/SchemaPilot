package org.sainm.schemapilot.skill;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.model.SkillStatus;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SkillExecutor {
    private final SkillRegistry registry;
    private final ManualSqlAnalysisService analysisService;

    public SkillExecutor(SkillRegistry registry, ManualSqlAnalysisService analysisService) {
        this.registry = registry;
        this.analysisService = analysisService;
    }

    public SkillRun execute(String skillId, String sql) {
        var definition = registry.get(skillId);
        if (definition.status() != SkillStatus.ENABLED) {
            throw new BadRequestException("Skill is not enabled: " + skillId);
        }
        var analysis = analysisService.analyze(sql);
        if (analysis.statements().isEmpty()) {
            throw new BadRequestException("Skill input did not produce any statement.");
        }
        var statement = analysis.statements().getFirst();
        var output = Map.<String, Object>of(
                "objectType", statement.objectType().name(),
                "objectName", statement.objectName(),
                "targetSql", statement.postgresSql(),
                "risks", statement.risks().stream().map(risk -> risk.type()).toList(),
                "reviewRequired", definition.requiresReview() || !statement.parseIssues().isEmpty()
        );
        return new SkillRun(
                UUID.randomUUID(),
                definition.id(),
                definition.version(),
                validate(definition, output),
                definition.requiresReview(),
                output,
                Instant.now()
        );
    }

    private boolean validate(SkillDefinition definition, Map<String, Object> output) {
        for (var field : definition.outputSchema().keySet()) {
            if (!output.containsKey(field)) {
                return false;
            }
        }
        return true;
    }
}
