package org.sainm.schemapilot.datasource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataSourceConnectionTestResult(
        UUID configId,
        boolean success,
        String databaseProduct,
        String databaseVersion,
        String username,
        List<String> permissions,
        List<String> risks,
        String message,
        Instant testedAt
) {
}
