package org.sainm.schemapilot.ai;

import org.springframework.stereotype.Component;

@Component
class AiContextBuilder {
    private final SensitiveValueRedactor redactor;

    AiContextBuilder(SensitiveValueRedactor redactor) {
        this.redactor = redactor;
    }

    String riskContext(RiskExplanationRequest request) {
        return """
                riskType=%s
                objectType=%s
                message=%s
                originalSql=%s
                """.formatted(
                redactor.redact(request.riskType()),
                redactor.redact(request.objectType()),
                redactor.redact(request.message()),
                redactor.redact(request.originalSql())
        ).strip();
    }

    String sqlRewriteContext(SqlRewriteRequest request) {
        return """
                objectType=%s
                riskTypes=%s
                originalSql=%s
                postgresSql=%s
                """.formatted(
                redactor.redact(request.objectType()),
                request.riskTypes().stream().map(redactor::redact).toList(),
                redactor.redact(request.originalSql()),
                redactor.redact(request.postgresSql())
        ).strip();
    }

    String plsqlContext(PlsqlExplanationRequest request) {
        return """
                objectType=%s
                objectName=%s
                originalSql=%s
                postgresDraft=%s
                """.formatted(
                redactor.redact(request.objectType()),
                redactor.redact(request.objectName()),
                redactor.redact(request.originalSql()),
                redactor.redact(request.postgresDraft())
        ).strip();
    }

    String precheckContext(PrecheckSummaryRequest request) {
        return """
                statementCount=%d
                riskCount=%d
                highRiskCount=%d
                blockerCount=%d
                topRiskTypes=%s
                """.formatted(
                request.statementCount(),
                request.riskCount(),
                request.highRiskCount(),
                request.blockerCount(),
                request.topRiskTypes().stream().map(redactor::redact).toList()
        ).strip();
    }

    String executionErrorContext(ExecutionErrorDiagnosisRequest request) {
        return """
                phase=%s
                objectName=%s
                objectType=%s
                sql=%s
                errorMessage=%s
                """.formatted(
                redactor.redact(request.phase()),
                redactor.redact(request.objectName()),
                redactor.redact(request.objectType()),
                redactor.redact(request.sql()),
                redactor.redact(request.errorMessage())
        ).strip();
    }

    String validationDiffContext(ValidationDiffDiagnosisRequest request) {
        return """
                objectName=%s
                checkType=%s
                sourceValue=%s
                targetValue=%s
                issueCodes=%s
                """.formatted(
                redactor.redact(request.objectName()),
                redactor.redact(request.checkType()),
                redactor.redact(request.sourceValue()),
                redactor.redact(request.targetValue()),
                request.issueCodes() == null ? java.util.List.of() : request.issueCodes().stream().map(redactor::redact).toList()
        ).strip();
    }

    String ruleCandidateContext(RuleCandidateRequest request) {
        return """
                source=%s
                objectType=%s
                originalSql=%s
                revisedSql=%s
                riskTypes=%s
                """.formatted(
                redactor.redact(request.source()),
                redactor.redact(request.objectType()),
                redactor.redact(request.originalSql()),
                redactor.redact(request.revisedSql()),
                request.riskTypes() == null ? java.util.List.of() : request.riskTypes().stream().map(redactor::redact).toList()
        ).strip();
    }

    String projectQuestionContext(ProjectQuestionRequest request) {
        return """
                question=%s
                projectSummary=%s
                topRiskTypes=%s
                objectNames=%s
                """.formatted(
                redactor.redact(request.question()),
                redactor.redact(request.projectSummary()),
                request.topRiskTypes() == null ? java.util.List.of() : request.topRiskTypes().stream().map(redactor::redact).toList(),
                request.objectNames() == null ? java.util.List.of() : request.objectNames().stream().map(redactor::redact).toList()
        ).strip();
    }

    String longPlsqlContext(LongPlsqlSummaryRequest request) {
        return """
                objectName=%s
                objectType=%s
                chunkSize=%d
                sourceSql=%s
                """.formatted(
                redactor.redact(request.objectName()),
                redactor.redact(request.objectType()),
                request.chunkSize(),
                redactor.redact(request.sourceSql())
        ).strip();
    }

    String packageModernizationContext(PackageModernizationRequest request) {
        return """
                objectName=%s
                packageSql=%s
                """.formatted(
                redactor.redact(request.objectName()),
                redactor.redact(request.packageSql())
        ).strip();
    }

    String historicalRuleTemplateContext(HistoricalRuleTemplateRequest request) {
        return """
                objectType=%s
                riskTypes=%s
                historicalCases=%s
                """.formatted(
                redactor.redact(request.objectType()),
                request.riskTypes() == null ? java.util.List.of() : request.riskTypes().stream().map(redactor::redact).toList(),
                request.historicalCases() == null ? java.util.List.of() : request.historicalCases().stream().map(redactor::redact).toList()
        ).strip();
    }
}
