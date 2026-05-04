package org.sainm.schemapilot.app.input;

import java.util.UUID;

record ManualSqlInputRequest(
        UUID sourceProjectId,
        String name,
        String sql) {
}
