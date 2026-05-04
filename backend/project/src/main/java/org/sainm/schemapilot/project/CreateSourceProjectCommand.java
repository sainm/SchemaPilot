package org.sainm.schemapilot.project;

import java.util.UUID;

public record CreateSourceProjectCommand(
        UUID projectId,
        String name,
        SourceProjectType type) {
}
