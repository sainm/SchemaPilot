package org.sainm.schemapilot.oracle;

import java.util.List;

public record OracleScanSnapshot(
        String schemaName,
        List<String> schemas,
        List<OracleObjectMetadata> objects,
        List<OracleColumnMetadata> columns,
        List<OracleConstraintMetadata> constraints,
        List<OracleIndexMetadata> indexes,
        List<String> risks
) {
}
