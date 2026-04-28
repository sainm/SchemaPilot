package org.sainm.schemapilot.mcp;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration"
})
class McpGatewayTest {
    @MockitoBean
    ProjectRepository projectRepository;

    @Autowired
    McpGateway gateway;

    @Test
    void exposesReadOnlyResourcesPromptsAndDisabledExternalClientStatus() {
        assertThat(gateway.status())
                .containsEntry("externalClientEnabled", false)
                .containsEntry("writeToolsDefaultDryRun", true);
        assertThat(gateway.resources()).extracting(McpResource::id).contains("migration-risk-rules", "skill-catalog");
        assertThat(gateway.prompts()).extracting(McpPrompt::id).contains("explain-risk", "draft-conversion", "diagnose-error");
    }

    @Test
    void allowlistedReadToolRunsAndIsAudited() {
        var record = gateway.callTool(new McpToolCallRequest(
                "sql.analyze",
                Map.of("sql", "CREATE TABLE users (id NUMBER(19) PRIMARY KEY);"),
                false
        ));

        assertThat(record.allowed()).isTrue();
        assertThat(record.success()).isTrue();
        assertThat(gateway.toolCalls()).extracting(McpToolCallRecord::id).contains(record.id());
    }

    @Test
    void nonAllowlistedToolIsDeniedAndAudited() {
        var record = gateway.callTool(new McpToolCallRequest(
                "database.drop",
                Map.of("sql", "drop table users"),
                false
        ));

        assertThat(record.allowed()).isFalse();
        assertThat(record.success()).isFalse();
        assertThat(record.message()).contains("not allowlisted");
        assertThat(gateway.toolCalls()).extracting(McpToolCallRecord::id).contains(record.id());
    }

    @Test
    void writeLikeToolDefaultsToDryRunAndCannotBypassGate() {
        var dryRunRecord = gateway.callTool(new McpToolCallRequest(
                "skill.run",
                Map.of("skillId", "oracle-table-ddl", "sql", "CREATE TABLE users (id NUMBER(19));"),
                null
        ));
        var blockedRecord = gateway.callTool(new McpToolCallRequest(
                "skill.run",
                Map.of("skillId", "oracle-table-ddl", "sql", "CREATE TABLE users (id NUMBER(19));"),
                false
        ));

        assertThat(dryRunRecord.allowed()).isTrue();
        assertThat(dryRunRecord.success()).isTrue();
        assertThat(dryRunRecord.usedDefaultDryRun()).isTrue();
        assertThat(blockedRecord.allowed()).isFalse();
        assertThat(blockedRecord.message()).contains("dryRun=true");
    }
}
