package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveManualSqlRequest(
        @NotBlank
        @Size(max = 200000)
        String sql
) {
}
