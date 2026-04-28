package org.sainm.schemapilot.precheck;

import org.sainm.schemapilot.ai.AiProvider;
import org.sainm.schemapilot.ai.AiSuggestionDraft;
import org.sainm.schemapilot.ai.PlsqlExplanationRequest;
import org.sainm.schemapilot.ai.PrecheckSummaryRequest;
import org.sainm.schemapilot.ai.RiskExplanationRequest;
import org.sainm.schemapilot.ai.SqlRewriteRequest;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrecheckReportServiceTest {
    private final PrecheckReportService service = new PrecheckReportService(
            new ManualSqlAnalysisService(
                    new SqlStatementSplitter(),
                    new SqlObjectClassifier(),
                    new OracleToPostgresConverter(),
                    new SqlRiskDetector(),
                    new RiskScoringService()
            ),
            new StubAiProvider()
    );

    @Test
    void generatesPrecheckSnapshotFromManualSql() {
        var report = service.generate("""
                CREATE TABLE users (
                  id NUMBER(19) PRIMARY KEY,
                  name VARCHAR2(100),
                  created_at DATE DEFAULT SYSDATE
                );

                CREATE OR REPLACE PACKAGE pkg_users AS
                  g_counter NUMBER := 0;
                  PROCEDURE sync_users;
                END pkg_users;
                /
                """);

        assertThat(report.reportVersion()).startsWith("precheck-");
        assertThat(report.analysis().statementCount()).isEqualTo(2);
        assertThat(report.analysis().compatibilityScore()).isLessThan(100);
        assertThat(report.objectTypeDistribution()).containsEntry(ObjectType.TABLE, 1L);
        assertThat(report.objectTypeDistribution()).containsEntry(ObjectType.PACKAGE, 1L);
        assertThat(report.riskDistribution()).containsKey(RiskLevel.BLOCKER);
        assertThat(report.highRiskObjects())
                .extracting(HighRiskObject::objectName)
                .contains("pkg_users");
        assertThat(report.typeMappings())
                .extracting(TypeMappingItem::sourceType)
                .contains("NUMBER(p,s)", "VARCHAR2/NVARCHAR2", "DATE");
        assertThat(report.issues())
                .extracting(SqlIssueItem::riskType)
                .contains("PACKAGE", "PACKAGE_GLOBAL_STATE");
        assertThat(report.handlingRecommendations()).isNotEmpty();
        assertThat(report.migrationOrderDraft().getFirst()).startsWith("TABLE");
        assertThat(report.managementSummary()).contains("summary");
        assertThat(report.developerSummary()).contains("pkg_users");
        assertThat(report.versionRefs()).containsKeys("objectVersion", "conversionVersion", "riskVersion", "aiSuggestionVersion");
    }

    private static class StubAiProvider implements AiProvider {
        @Override
        public AiSuggestionDraft explainRisk(RiskExplanationRequest request) {
            return draft("risk");
        }

        @Override
        public AiSuggestionDraft suggestSqlRewrite(SqlRewriteRequest request) {
            return draft("rewrite");
        }

        @Override
        public AiSuggestionDraft explainPlsqlDraft(PlsqlExplanationRequest request) {
            return draft("plsql");
        }

        @Override
        public AiSuggestionDraft summarizePrecheck(PrecheckSummaryRequest request) {
            return draft("summary " + request.statementCount() + "/" + request.riskCount());
        }

        private AiSuggestionDraft draft(String suggestion) {
            return new AiSuggestionDraft("stub", "stub", "stub-v1", suggestion, List.of("test"), List.of("risk.date"));
        }
    }
}
