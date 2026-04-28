package org.sainm.schemapilot.workbench;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        String action,
        String message,
        Instant createdAt
) {
    static AuditEvent of(String action, String message) {
        return new AuditEvent(UUID.randomUUID(), action, message, Instant.now());
    }
}
