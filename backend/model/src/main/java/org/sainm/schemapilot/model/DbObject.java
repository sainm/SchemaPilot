package org.sainm.schemapilot.model;

import java.util.List;
import java.util.UUID;

public record DbObject(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        DbObjectType type,
        String schema,
        String name,
        ObjectStatus status,
        SourceLocation sourceLocation,
        String originalSql,
        List<DbColumn> columns) {

    public DbObject {
        columns = List.copyOf(columns);
    }
}
