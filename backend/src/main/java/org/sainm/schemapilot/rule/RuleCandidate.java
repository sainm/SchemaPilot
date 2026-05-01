package org.sainm.schemapilot.rule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuleCandidate(
        UUID id,
        RuleCandidateSource source,
        String sourceProject,
        String reviewer,
        String objectType,
        String riskType,
        String pattern,
        String replacement,
        RuleCandidateStatus status,
        List<RuleFixture> fixtures,
        Instant createdAt,
        Instant updatedAt
) {
}
