package org.sainm.schemapilot.model;

import java.util.UUID;

public record ParseIssue(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        String code,
        String message,
        SourceLocation sourceLocation,
        String originalText) {
}
