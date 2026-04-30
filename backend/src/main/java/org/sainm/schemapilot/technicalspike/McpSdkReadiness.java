package org.sainm.schemapilot.technicalspike;

import java.util.List;
import java.util.Map;

public record McpSdkReadiness(
        boolean readyForAdapter,
        String sdkCoordinate,
        Map<String, String> schemaPilotMapping,
        List<String> integrationSteps,
        List<String> risks
) {
}
