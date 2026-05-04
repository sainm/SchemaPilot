package org.sainm.schemapilot.input;

import java.util.UUID;

public record ManualSqlInputCommand(
        UUID projectId,
        UUID sourceProjectId,
        String name,
        String sql) {
}
