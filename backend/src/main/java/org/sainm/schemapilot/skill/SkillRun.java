package org.sainm.schemapilot.skill;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SkillRun(
        UUID id,
        String skillId,
        String skillVersion,
        boolean valid,
        boolean requiresReview,
        Map<String, Object> output,
        Instant createdAt
) {
}
