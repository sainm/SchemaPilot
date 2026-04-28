package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank
        String reviewer,
        @Size(max = 4000)
        String comment
) {
    String safeComment() {
        return comment == null ? "" : comment;
    }
}
