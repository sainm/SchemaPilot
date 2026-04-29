package org.sainm.schemapilot.rule;

public record RuleFixture(
        String name,
        String inputSql,
        String expectedSql,
        boolean shouldMatch
) {
}
