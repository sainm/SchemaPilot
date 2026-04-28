package org.sainm.schemapilot.skill;

import java.util.List;
import java.util.Map;

public record SkillYamlSpec(
        String id,
        String version,
        String status,
        String description,
        List<String> allowedTools,
        boolean requiresReview,
        Map<String, String> outputSchema
) {
}
