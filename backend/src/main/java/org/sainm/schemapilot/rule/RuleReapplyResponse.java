package org.sainm.schemapilot.rule;

import java.util.List;

public record RuleReapplyResponse(
        String originalSql,
        String revisedSql,
        List<String> appliedRuleIds
) {
}
