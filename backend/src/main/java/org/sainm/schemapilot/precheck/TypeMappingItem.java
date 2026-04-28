package org.sainm.schemapilot.precheck;

public record TypeMappingItem(
        int statementIndex,
        String objectName,
        String sourceType,
        String targetType
) {
}
