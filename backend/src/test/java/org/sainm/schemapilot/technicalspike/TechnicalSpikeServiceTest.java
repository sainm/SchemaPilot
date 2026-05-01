package org.sainm.schemapilot.technicalspike;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.sainm.schemapilot.datasource.DataSourceConfigService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalSpikeServiceTest {
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor();
    private final TechnicalSpikeService service = new TechnicalSpikeService(null, redactor);

    @Test
    void reportsSpringAiPgVectorReadinessWithoutOwningTableCreation() {
        var readiness = service.springAiPgVectorReadiness();

        assertThat(readiness.readyForAdapter()).isTrue();
        assertThat(readiness.dependencyCoordinate()).contains("spring-ai-starter-vector-store-pgvector");
        assertThat(readiness.recommendedProperties())
                .containsEntry("spring.ai.vectorstore.pgvector.initialize-schema", "false")
                .containsEntry("spring.ai.vectorstore.pgvector.remove-existing-vector-store-table", "false");
        assertThat(readiness.integrationSteps()).anyMatch(step -> step.contains("Flyway"));
        assertThat(readiness.risks()).anyMatch(risk -> risk.contains("Embedding dimension"));
    }

    @Test
    void reportsMcpSdkReadinessPreservingSecurityBoundary() {
        var readiness = service.mcpSdkReadiness();

        assertThat(readiness.readyForAdapter()).isTrue();
        assertThat(readiness.sdkCoordinate()).contains("modelcontextprotocol");
        assertThat(readiness.schemaPilotMapping()).containsKeys("McpResource", "McpPrompt", "ToolRegistry", "McpGateway allowlist");
        assertThat(readiness.integrationSteps()).anyMatch(step -> step.contains("security boundary"));
        assertThat(readiness.risks()).anyMatch(risk -> risk.contains("dry-run"));
    }

    @Test
    void redactsJdbcUrlsFromPgVectorProbeFailures() {
        var dataSourceService = new FailingDataSourceConfigService(
                "connection failed for jdbc:postgresql://pilot:secret@db.example:5432/app");
        var probeService = new TechnicalSpikeService(dataSourceService, redactor);

        var result = probeService.probePgVector(new PgVectorProbeRequest(UUID.randomUUID(), false));

        assertThat(result.risks()).anyMatch(risk -> risk.contains("jdbc:postgresql:<redacted>"));
        assertThat(result.risks()).noneMatch(risk -> risk.contains("secret@db.example"));
    }

    private static final class FailingDataSourceConfigService extends DataSourceConfigService {
        private final String message;

        private FailingDataSourceConfigService(String message) {
            super(null, null, null);
            this.message = message;
        }

        @Override
        public Connection openConnection(UUID configId) throws SQLException {
            throw new SQLException(message);
        }
    }
}
