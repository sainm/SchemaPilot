package org.sainm.schemapilot.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveDataSourceConfigRequest(
        @NotBlank
        @Size(max = 120)
        String name,
        @NotNull
        DataSourceKind kind,
        @NotBlank
        @Size(max = 1000)
        String jdbcUrl,
        @NotBlank
        @Size(max = 120)
        String username,
        @Size(max = 500)
        String password
) {
}
