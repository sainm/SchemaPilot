package org.sainm.schemapilot.validation;

public record ChecksumCostRequest(
        int rowCount,
        int shardCount,
        int columnCount,
        int valueBytes
) {
}
