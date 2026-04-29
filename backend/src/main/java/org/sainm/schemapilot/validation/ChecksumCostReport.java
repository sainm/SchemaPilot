package org.sainm.schemapilot.validation;

public record ChecksumCostReport(
        int rowCount,
        int shardCount,
        long elapsedMillis,
        double rowsPerSecond,
        long estimatedPayloadBytes,
        long offHeapBytesAfter,
        long activeArenasAfter
) {
}
