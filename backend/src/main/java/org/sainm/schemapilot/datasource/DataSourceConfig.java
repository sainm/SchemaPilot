package org.sainm.schemapilot.datasource;

import java.time.Instant;
import java.util.UUID;

record DataSourceConfig(
        UUID id,
        String name,
        DataSourceKind kind,
        String jdbcUrl,
        String username,
        String encryptedPassword,
        DataSourceConfigStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
