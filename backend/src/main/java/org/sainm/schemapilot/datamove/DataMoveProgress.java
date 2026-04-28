package org.sainm.schemapilot.datamove;

public record DataMoveProgress(
        long rowsRead,
        long rowsWritten
) {
}
