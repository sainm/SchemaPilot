package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;

public record LongPlsqlSummaryRequest(
        @NotBlank
        String objectName,
        String objectType,
        @NotBlank
        String sourceSql,
        int chunkSize
) {
}
