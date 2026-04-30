package org.sainm.schemapilot.technicalspike;

import java.util.List;
import java.util.Map;

public record SpringAiPgVectorReadiness(
        boolean readyForAdapter,
        String dependencyCoordinate,
        String tableName,
        String embeddingColumn,
        String contentColumn,
        String metadataColumn,
        Map<String, String> recommendedProperties,
        List<String> integrationSteps,
        List<String> risks
) {
}
