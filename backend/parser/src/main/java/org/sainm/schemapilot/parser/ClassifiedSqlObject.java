package org.sainm.schemapilot.parser;

import org.sainm.schemapilot.model.DbObjectType;

public record ClassifiedSqlObject(
        DbObjectType objectType,
        String schema,
        String name,
        String originalSql) {
}
