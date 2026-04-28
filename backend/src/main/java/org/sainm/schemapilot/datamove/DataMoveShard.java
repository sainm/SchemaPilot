package org.sainm.schemapilot.datamove;

public record DataMoveShard(
        int index,
        DataMoveShardStrategy strategy,
        String predicate,
        Long lowerBound,
        Long upperBound,
        long rowsRead,
        long rowsWritten,
        DataMoveShardStatus status,
        String checkpoint
) {
    public static DataMoveShard single() {
        return new DataMoveShard(0, DataMoveShardStrategy.SINGLE, null, null, null, 0, 0, DataMoveShardStatus.PENDING, "SINGLE:0:0");
    }

    public DataMoveShard running() {
        return withProgress(rowsRead, rowsWritten, DataMoveShardStatus.RUNNING);
    }

    public DataMoveShard completed(long newRowsRead, long newRowsWritten) {
        return withProgress(newRowsRead, newRowsWritten, DataMoveShardStatus.COMPLETED);
    }

    public DataMoveShard failed(long newRowsRead, long newRowsWritten) {
        return withProgress(newRowsRead, newRowsWritten, DataMoveShardStatus.FAILED);
    }

    public DataMoveShard withProgress(long newRowsRead, long newRowsWritten, DataMoveShardStatus newStatus) {
        return new DataMoveShard(index, strategy, predicate, lowerBound, upperBound, newRowsRead, newRowsWritten, newStatus, strategy + ":" + index + ":" + newRowsWritten);
    }
}
