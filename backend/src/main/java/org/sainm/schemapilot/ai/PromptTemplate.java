package org.sainm.schemapilot.ai;

import java.time.Instant;

public record PromptTemplate(
        String key,
        String version,
        String purpose,
        String template,
        boolean active,
        Instant updatedAt
) {
}
