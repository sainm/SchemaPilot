package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EditSqlVersionRequest(
        @Positive
        int statementIndex,
        @NotBlank
        @Size(max = 12000)
        String targetSql
) {
}
