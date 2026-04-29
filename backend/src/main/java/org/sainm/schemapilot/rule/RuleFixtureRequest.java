package org.sainm.schemapilot.rule;

import jakarta.validation.constraints.NotBlank;

public record RuleFixtureRequest(
        @NotBlank
        String name,
        @NotBlank
        String inputSql,
        @NotBlank
        String expectedSql,
        boolean shouldMatch
) {
}
