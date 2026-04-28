package org.sainm.schemapilot.datamove;

public record DataMoveTableProfile(
        long estimatedRows,
        boolean largeTable,
        String shardColumn,
        NumericBounds numericBounds
) {
}
