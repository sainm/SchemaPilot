package org.sainm.schemapilot.dependency;

import java.util.UUID;

public record ObjectDependency(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID sourceObjectId,
        UUID targetObjectId,
        DependencyType type,
        String evidence) {
}
