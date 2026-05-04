package org.sainm.schemapilot.parser;

import java.util.UUID;

public record AssetModelingCommand(
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        String sourcePath,
        String sqlText) {
}
