package org.sainm.schemapilot.project;

import java.time.Instant;
import java.util.UUID;

public record MigrationProject(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
