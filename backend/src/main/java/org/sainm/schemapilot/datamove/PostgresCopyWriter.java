package org.sainm.schemapilot.datamove;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface PostgresCopyWriter {
    long copy(UUID targetDataSourceId, String targetTable, List<String> columns, Iterable<TableRow> rows, Consumer<DataMoveProgress> progressConsumer);
}
