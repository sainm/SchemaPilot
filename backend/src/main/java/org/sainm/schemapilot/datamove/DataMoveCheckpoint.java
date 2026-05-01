package org.sainm.schemapilot.datamove;

public record DataMoveCheckpoint(
        int nextShardIndex,
        long rowsRead,
        long rowsWritten,
        String token
) {
    public static DataMoveCheckpoint start() {
        return new DataMoveCheckpoint(0, 0, 0, "start");
    }

    public DataMoveCheckpoint advance(DataMoveShard shard, long totalRowsRead, long totalRowsWritten) {
        return new DataMoveCheckpoint(shard.index() + 1, totalRowsRead, totalRowsWritten, shard.checkpoint());
    }
}
