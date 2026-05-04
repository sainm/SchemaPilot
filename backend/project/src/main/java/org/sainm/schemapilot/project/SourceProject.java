package org.sainm.schemapilot.project;

import java.time.Instant;
import java.util.UUID;

public record SourceProject(
        UUID id,
        UUID projectId,
        String name,
        SourceProjectType type,
        Instant createdAt) {
}
