package org.sainm.schemapilot.sql;

public record ParseIssue(
        String type,
        String message,
        String suggestion
) {
}
