package org.sainm.schemapilot.technicalspike;

import java.util.List;

public record PgVectorProbeResult(
        boolean extensionInstalled,
        boolean extensionAvailable,
        String installedVersion,
        String availableVersion,
        boolean createAttempted,
        boolean createSucceeded,
        List<String> risks,
        List<String> nextSteps
) {
}
