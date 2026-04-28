package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ConversionLevel;

public record ConversionDraft(
        String postgresSql,
        ConversionLevel level
) {
}
