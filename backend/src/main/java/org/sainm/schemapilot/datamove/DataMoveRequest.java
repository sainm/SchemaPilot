package org.sainm.schemapilot.datamove;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DataMoveRequest(
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
        long expectedRows,
        long estimatedRows,
        long largeTableThreshold,
        long shardSize,
        String shardColumn,
        int projectConcurrencyLimit,
        int globalConcurrencyLimit,
        int tableConcurrencyLimit,
        long rateLimitRowsPerSecond
) {
    public DataMoveRequest(UUID sourceDataSourceId, UUID targetDataSourceId, String sourceTable, String targetTable, List<String> columns, long expectedRows) {
        this(sourceDataSourceId, targetDataSourceId, sourceTable, targetTable, columns, expectedRows, -1, 100_000, 50_000, null, 4, 8, 1, 0);
    }
}
