package org.sainm.schemapilot.risk;

import java.util.UUID;

import org.sainm.schemapilot.rule.RuleHit;

public record ObjectRiskIssue(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        UUID objectId,
        String code,
        RiskLevel level,
        String message,
        String evidence,
        RuleHit ruleHit) {
}
