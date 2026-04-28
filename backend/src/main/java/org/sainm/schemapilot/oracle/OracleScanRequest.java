package org.sainm.schemapilot.oracle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OracleScanRequest(
        @NotNull
        UUID dataSourceId,
        @NotBlank
        String schemaName
) {
    public String normalizedSchema() {
        return schemaName.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
