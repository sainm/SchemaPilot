package org.sainm.schemapilot.validation;

public record ShardChecksum(
        int shardIndex,
        long rows,
        long checksum
) {
}
