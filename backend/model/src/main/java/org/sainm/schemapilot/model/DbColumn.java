package org.sainm.schemapilot.model;

public record DbColumn(
        String name,
        String sourceType,
        String targetType,
        boolean nullable,
        Integer ordinalPosition) {
}
