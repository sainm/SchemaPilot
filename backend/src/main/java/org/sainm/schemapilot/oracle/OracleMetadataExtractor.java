package org.sainm.schemapilot.oracle;

import java.util.UUID;
import java.util.function.Consumer;

public interface OracleMetadataExtractor {
    OracleScanSnapshot extract(UUID dataSourceId, String schemaName, Consumer<OracleScanProgress> progressConsumer);
}
