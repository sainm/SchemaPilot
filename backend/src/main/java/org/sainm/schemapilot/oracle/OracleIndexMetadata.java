package org.sainm.schemapilot.oracle;

import java.util.List;

public record OracleIndexMetadata(
        String name,
        String tableName,
        boolean unique,
        List<String> columns
) {
}
