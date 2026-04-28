package org.sainm.schemapilot.skill;

import org.sainm.schemapilot.model.SkillStatus;

import java.util.List;
import java.util.Map;

public record SkillDefinition(
        String id,
        String version,
        SkillStatus status,
        String description,
        List<String> allowedTools,
        boolean requiresReview,
        Map<String, String> outputSchema
) {
}
