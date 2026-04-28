package org.sainm.schemapilot.model;

public enum AgentStatus {
    CREATED,
    PLANNING,
    WAITING_FOR_TOOL,
    THINKING,
    PRODUCING,
    AWAITING_USER_DECISION,
    APPLIED,
    REJECTED,
    AUDITED,
    FAILED
}
