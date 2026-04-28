package org.sainm.schemapilot.rule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuleConfigUpdateResponse(
        String name,
        String version,
        Instant updatedAt,
        List<UUID> expiredSnapshotIds
) {
}
