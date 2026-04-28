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
}
