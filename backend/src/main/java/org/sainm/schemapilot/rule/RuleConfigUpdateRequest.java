package org.sainm.schemapilot.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuleConfigUpdateRequest(
        @NotBlank
        String name,
        @NotBlank
        String version,
        @Size(max = 12000)
        String content
) {
}
