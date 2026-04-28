package org.sainm.schemapilot.sql;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualSqlAnalyzeRequest(
        @NotBlank
        @Size(max = 200_000)
        String sql
) {
}
