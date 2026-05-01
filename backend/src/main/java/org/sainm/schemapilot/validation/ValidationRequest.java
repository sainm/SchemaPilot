package org.sainm.schemapilot.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ValidationRequest(
        @NotNull
        UUID sourceDataSourceId,
        @NotNull
        UUID targetDataSourceId,
        @NotBlank
        String sourceTable,
        @NotBlank
        String targetTable,
        @NotEmpty
        List<String> columns,
        String keyColumn,
        int sampleSize,
        int checksumShardCount,
        List<String> views,
        List<String> routines
) {
}
