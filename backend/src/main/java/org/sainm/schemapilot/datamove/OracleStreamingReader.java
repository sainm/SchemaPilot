package org.sainm.schemapilot.datamove;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface OracleStreamingReader {
    long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer);
}
