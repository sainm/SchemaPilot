package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ObjectType;

public record ConversionContext(
        ObjectType objectType,
        String objectName,
        String originalSql
) {
    static ConversionContext from(ClassifiedStatement statement, String originalSql) {
        return new ConversionContext(statement.objectType(), statement.objectName(), originalSql);
    }
}
