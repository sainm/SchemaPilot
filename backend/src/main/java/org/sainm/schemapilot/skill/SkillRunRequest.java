package org.sainm.schemapilot.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRunRequest(
        @NotBlank
        @Size(max = 12000)
        String sql
) {
}
