package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;

public record PackageModernizationRequest(
        @NotBlank
        String objectName,
        @NotBlank
        String packageSql
) {
}
