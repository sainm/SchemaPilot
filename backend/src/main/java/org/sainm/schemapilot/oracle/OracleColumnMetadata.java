package org.sainm.schemapilot.oracle;

public record OracleColumnMetadata(
        String tableName,
        String columnName,
        String dataType,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        String comment
) {
}
