package org.sainm.schemapilot.common.audit;

public record AuditActor(
        String actorId,
        String displayName,
        AuditActorType type) {

    public static AuditActor system() {
        return new AuditActor("system", "System", AuditActorType.SYSTEM);
    }
}
