package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyEditedAiSuggestionRequest(
        @NotBlank
        @Size(max = 12000)
        String targetSql
) {
}
