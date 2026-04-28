package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.model.SqlBaselineStatus;

import java.time.Instant;
import java.util.UUID;

public record SavedSqlVersion(
        UUID id,
        int statementIndex,
        SqlVersionSource source,
        SqlBaselineStatus status,
        String sql,
        Instant createdAt
) {
    SavedSqlVersion withStatus(SqlBaselineStatus nextStatus) {
        return new SavedSqlVersion(id, statementIndex, source, nextStatus, sql, createdAt);
    }
}
