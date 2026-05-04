package org.sainm.schemapilot.risk;

public record RiskIssue(
        String code,
        RiskLevel level,
        String message,
        String evidence) {
}
