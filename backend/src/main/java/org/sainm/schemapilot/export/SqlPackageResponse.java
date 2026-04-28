package org.sainm.schemapilot.export;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SqlPackageResponse(
        UUID snapshotId,
        String fileName,
        Instant generatedAt,
        List<UUID> sqlVersionIds,
        String content
) {
}
