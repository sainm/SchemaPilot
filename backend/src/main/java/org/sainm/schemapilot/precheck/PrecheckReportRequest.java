package org.sainm.schemapilot.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrecheckReportRequest(
        @NotBlank
        @Size(max = 200000)
        String sql
) {
}
