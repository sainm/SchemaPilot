package org.sainm.schemapilot.datasource;

import java.time.Instant;
import java.util.UUID;

public record DataSourceConfigResponse(
        UUID id,
        String name,
        DataSourceKind kind,
        String jdbcUrl,
        String username,
        boolean passwordConfigured,
        DataSourceConfigStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static DataSourceConfigResponse from(DataSourceConfig config) {
        return new DataSourceConfigResponse(
                config.id(),
                config.name(),
                config.kind(),
                sanitizeJdbcUrl(config.jdbcUrl()),
                config.username(),
                config.encryptedPassword() != null && !config.encryptedPassword().isBlank(),
                config.status(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    private static String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        return jdbcUrl
                .replaceAll("(?i)(password=)[^;&]+", "$1<redacted>")
                .replaceAll("(?i)(pwd=)[^;&]+", "$1<redacted>")
                .replaceAll("(?i)(sslpassword=)[^;&]+", "$1<redacted>");
    }
}
