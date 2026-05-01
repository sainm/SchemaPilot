package org.sainm.schemapilot.datamove;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface OracleStreamingReader {
    long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer);

    default long streamShard(UUID sourceDataSourceId, String sourceTable, List<String> columns, DataMoveShard shard, Consumer<TableRow> rowConsumer) {
        if (shard.strategy() == DataMoveShardStrategy.SINGLE) {
            return stream(sourceDataSourceId, sourceTable, columns, rowConsumer);
        }
        throw new DataMoveException("Oracle streaming reader does not support shard predicates.", new UnsupportedOperationException(shard.strategy().name()));
    }

    default long estimateRows(UUID sourceDataSourceId, String sourceTable) {
        return -1;
    }

    default NumericBounds numericBounds(UUID sourceDataSourceId, String sourceTable, String shardColumn) {
        return NumericBounds.unknown();
    }
}
