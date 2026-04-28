package org.sainm.schemapilot.sql;

import java.util.List;

public record SqlAnalysisResponse(
        List<AnalyzedStatement> statements,
        int statementCount,
        int riskCount,
        int compatibilityScore
) {
    public static SqlAnalysisResponse of(List<AnalyzedStatement> statements, int compatibilityScore) {
        var riskCount = statements.stream()
                .mapToInt(statement -> statement.risks().size())
                .sum();
        return new SqlAnalysisResponse(statements, statements.size(), riskCount, compatibilityScore);
    }
}
