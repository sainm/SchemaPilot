package org.sainm.schemapilot.validation;

import org.sainm.schemapilot.datamove.TableRow;

import java.util.List;
import java.util.UUID;

public interface ValidationProbe {
    boolean objectExists(UUID dataSourceId, String objectName);

    long rowCount(UUID dataSourceId, String tableName);

    List<TableRow> sampleRows(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int limit);

    List<ShardChecksum> shardChecksums(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int shardCount);

    boolean viewExecutable(UUID dataSourceId, String viewName);

    boolean routineCompiles(UUID dataSourceId, String routineName);
}
