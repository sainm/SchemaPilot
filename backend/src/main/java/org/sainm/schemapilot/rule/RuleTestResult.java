package org.sainm.schemapilot.rule;

public record RuleTestResult(
        String fixtureName,
        boolean passed,
        String actualSql,
        String expectedSql
) {
}
