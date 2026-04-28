package org.sainm.schemapilot.sql;

import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class ManualSqlAnalysisService {
    private final SqlStatementSplitter splitter;
    private final SqlObjectClassifier classifier;
    private final ObjectConverter converter;
    private final SqlRiskDetector riskDetector;
    private final RiskScoringService riskScoringService;

    public ManualSqlAnalysisService(
            SqlStatementSplitter splitter,
            SqlObjectClassifier classifier,
            ObjectConverter converter,
            SqlRiskDetector riskDetector,
            RiskScoringService riskScoringService
    ) {
        this.splitter = splitter;
        this.classifier = classifier;
        this.converter = converter;
        this.riskDetector = riskDetector;
        this.riskScoringService = riskScoringService;
    }

    public SqlAnalysisResponse analyze(String sql) {
        var statements = splitter.split(sql);
        var analyzed = new ArrayList<AnalyzedStatement>();

        for (int i = 0; i < statements.size(); i++) {
            var originalSql = statements.get(i);
            var classified = classifier.classify(originalSql);
            var conversion = converter.convert(ConversionContext.from(classified, originalSql));
            var risks = riskDetector.detect(classified, originalSql);
            var riskLevel = riskScoringService.highestLevel(risks);
            var parseIssues = parseIssues(classified, originalSql);

            analyzed.add(new AnalyzedStatement(
                    i + 1,
                    classified.objectType(),
                    classified.objectName(),
                    originalSql,
                    conversion.postgresSql(),
                    conversion.level(),
                    riskLevel,
                    risks,
                    parseIssues,
                    buildAiSuggestion(risks.size(), classified)
            ));
        }

        return SqlAnalysisResponse.of(analyzed, riskScoringService.compatibilityScore(analyzed));
    }

    private String buildAiSuggestion(int riskCount, ClassifiedStatement classified) {
        if (riskCount == 0) {
            return "No high-signal risks detected by the rule engine. Review generated SQL before freezing a baseline.";
        }
        return "Rule engine detected " + riskCount + " issue(s) for " + classified.objectType()
                + " " + classified.objectName() + ". AI copilot should explain these risks with project knowledge before review.";
    }

    private java.util.List<ParseIssue> parseIssues(ClassifiedStatement classified, String originalSql) {
        if (classified.objectType() != org.sainm.schemapilot.model.ObjectType.UNKNOWN) {
            return java.util.List.of();
        }
        var type = originalSql.stripLeading().regionMatches(true, 0, "BEGIN", 0, 5)
                || originalSql.stripLeading().regionMatches(true, 0, "DECLARE", 0, 7)
                ? "ANONYMOUS_PLSQL"
                : "UNRECOGNIZED_STATEMENT";
        var message = "Statement was preserved but could not be classified as a supported database object.";
        var suggestion = "Review the original SQL manually and decide whether it should become a PostgreSQL routine, migration step, or ignored artifact.";
        return java.util.List.of(new ParseIssue(type, message, suggestion));
    }
}
