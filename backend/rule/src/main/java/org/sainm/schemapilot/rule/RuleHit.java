package org.sainm.schemapilot.rule;

public record RuleHit(
        String ruleCode,
        String title,
        String sourceFragment,
        String explanation) {
}
