package org.sainm.schemapilot.validation;

public record ValidationIssue(
        String severity,
        String code,
        String objectName,
        Integer shardIndex,
        String message
) {
}
