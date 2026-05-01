package org.sainm.schemapilot.oracle;

import java.util.List;

public record OracleConstraintMetadata(
        String name,
        String tableName,
        String type,
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns,
        String searchCondition
) {
}
