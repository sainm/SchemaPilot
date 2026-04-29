package org.sainm.schemapilot.ai;

import java.util.List;

public record RuleCandidateRequest(
        String source,
        String objectType,
        String originalSql,
        String revisedSql,
        List<String> riskTypes
) {
}
