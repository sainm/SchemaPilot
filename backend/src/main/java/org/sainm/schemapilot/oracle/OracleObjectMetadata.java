package org.sainm.schemapilot.oracle;

import org.sainm.schemapilot.model.ObjectType;

public record OracleObjectMetadata(
        ObjectType type,
        String name,
        String status,
        String ddl,
        String source,
        String comment,
        String partitionInfo
) {
}
