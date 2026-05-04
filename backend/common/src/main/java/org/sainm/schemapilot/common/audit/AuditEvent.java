package org.sainm.schemapilot.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        AuditEventType type,
        AuditActor actor,
        String targetType,
        String targetId,
        Map<String, Object> metadata,
        Instant occurredAt) {

    public static AuditEvent system(AuditEventType type, String targetType, String targetId) {
        return new AuditEvent(UUID.randomUUID(), type, AuditActor.system(), targetType, targetId, Map.of(), Instant.now());
    }
}
