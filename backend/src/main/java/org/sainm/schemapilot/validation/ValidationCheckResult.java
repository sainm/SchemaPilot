package org.sainm.schemapilot.validation;

import java.util.List;

public record ValidationCheckResult(
        ValidationCheckType type,
        ValidationStatus status,
        String sourceValue,
        String targetValue,
        String details,
        List<ValidationIssue> issues
) {
    public static ValidationCheckResult passed(ValidationCheckType type, String sourceValue, String targetValue, String details) {
        return new ValidationCheckResult(type, ValidationStatus.PASSED, sourceValue, targetValue, details, List.of());
    }

    public static ValidationCheckResult failed(ValidationCheckType type, String sourceValue, String targetValue, String details, ValidationIssue issue) {
        return new ValidationCheckResult(type, ValidationStatus.FAILED, sourceValue, targetValue, details, List.of(issue));
    }
}
