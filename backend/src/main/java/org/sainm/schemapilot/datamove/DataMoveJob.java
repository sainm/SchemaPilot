package org.sainm.schemapilot.datamove;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataMoveJob(
        UUID id,
        UUID sourceDataSourceId,
        UUID targetDataSourceId,
        String sourceTable,
        String targetTable,
        List<String> columns,
        DataMoveStatus status,
        long rowsRead,
        long rowsWritten,
        double rowsPerSecond,
        long offHeapBytes,
        long activeArenas,
        int attempts,
        String errorMessage,
        String workItem,
        Instant createdAt,
        Instant updatedAt
) {
}
