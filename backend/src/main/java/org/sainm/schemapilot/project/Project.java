package org.sainm.schemapilot.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Project(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
