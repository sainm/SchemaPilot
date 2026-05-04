package org.sainm.schemapilot.convert;

import java.time.Instant;
import java.util.UUID;

public record SqlVersion(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        UUID objectId,
        UUID conversionResultId,
        UUID parentVersionId,
        int versionNumber,
        SqlVersionSource source,
        SqlVersionStatus status,
        String sourceSql,
        String targetSql,
        Instant createdAt) {
}
