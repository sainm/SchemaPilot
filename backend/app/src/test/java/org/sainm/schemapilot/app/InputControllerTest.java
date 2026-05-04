package org.sainm.schemapilot.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class InputControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void importManualSqlUnderProject() throws Exception {
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pilot",
                                  "description": "Oracle migration assessment"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectJson = projectResult.getResponse().getContentAsString();
        String projectId = JsonPath.read(projectJson, "$.data.project.id");
        String sourceProjectId = JsonPath.read(projectJson, "$.data.sourceProjects[0].id");

        mockMvc.perform(post("/api/projects/{projectId}/inputs/manual-sql", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceProjectId": "%s",
                                  "name": "table.sql",
                                  "sql": "create table users (id number(10,0)); create view v_users as select * from users;"
                                }
                                """.formatted(sourceProjectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.batch.status").value("IMPORTED"))
                .andExpect(jsonPath("$.data.source.type").value("MANUAL_SQL"))
                .andExpect(jsonPath("$.data.source.contentHash").isNotEmpty());

        mockMvc.perform(get("/api/projects/{projectId}/assets/objects", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TABLE"))
                .andExpect(jsonPath("$.data[0].name").value("users"))
                .andExpect(jsonPath("$.data[0].columns[0].sourceType").value("number(10,0)"));

        mockMvc.perform(get("/api/projects/{projectId}/risks", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("NUMBER_INTEGER_NARROWING_REVIEW"))
                .andExpect(jsonPath("$.data[0].ruleHit.ruleCode").value("NUMBER_INTEGER_NARROWING_REVIEW"));

        mockMvc.perform(get("/api/projects/{projectId}/conversions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].level").value("AUTO"))
                .andExpect(jsonPath("$.data[0].targetSql").value(org.hamcrest.Matchers.containsString("numeric(10,0)")));

        mockMvc.perform(get("/api/projects/{projectId}/dependencies", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("VIEW_REFERENCES_TABLE"));

        MvcResult versionResult = mockMvc.perform(get("/api/projects/{projectId}/sql-versions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("GENERATED"))
                .andReturn();
        String versionId = JsonPath.read(versionResult.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/projects/{projectId}/sql-versions/{versionId}/edits", projectId, versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetSql": "create table \\"users\\" (\\"id\\" numeric(10,0) not null);"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDITED"))
                .andExpect(jsonPath("$.data.parentVersionId").value(versionId));

        MvcResult reportResult = mockMvc.perform(post("/api/projects/{projectId}/reports/precheck", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageReport.type").value("PRECHECK"))
                .andExpect(jsonPath("$.data.precheckReport.objectCount").value(2))
                .andReturn();
        String reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.stageReport.id");

        mockMvc.perform(get("/api/projects/{projectId}/reports/{reportId}/json", projectId, reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRECHECK"));

        mockMvc.perform(post("/api/projects/{projectId}/reviews", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": "%s",
                                  "decision": "APPROVED",
                                  "reviewer": "dba",
                                  "comment": "P0 approved"
                                }
                                """.formatted(reportId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(get("/api/projects/{projectId}/reviews", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewer").value("dba"));

        mockMvc.perform(get("/api/projects/{projectId}/sql-versions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status").value(org.hamcrest.Matchers.hasItem("BASELINE")));

        mockMvc.perform(get("/api/projects/{projectId}/exports/sql-package/preview", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.baselineCount").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/projects/{projectId}/exports/sql-package.sql", projectId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("SchemaPilot SQL Package")));

        mockMvc.perform(post("/api/projects/{projectId}/ai/suggestions/REPORT_SUMMARY", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.ruleHits").isArray())
                .andExpect(jsonPath("$.data.changePlan").isArray())
                .andExpect(jsonPath("$.data.validationPlan").isArray());

        mockMvc.perform(get("/api/projects/{projectId}/ai/suggestions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("REPORT_SUMMARY"));
    }

    @Test
    void importMultipleFilesUnderProject() throws Exception {
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "File Pilot",
                                  "description": "File import"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectJson = projectResult.getResponse().getContentAsString();
        String projectId = JsonPath.read(projectJson, "$.data.project.id");
        String sourceProjectId = JsonPath.read(projectJson, "$.data.sourceProjects[0].id");

        mockMvc.perform(multipart("/api/projects/{projectId}/inputs/files", projectId)
                        .file(new MockMultipartFile("files", "schema/users.sql", "text/plain", "create table users (id number);".getBytes()))
                        .file(new MockMultipartFile("files", "views/v_users.sql", "text/plain", "create view v_users as select * from users;".getBytes()))
                        .param("sourceProjectId", sourceProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources").isArray())
                .andExpect(jsonPath("$.data.sources.length()").value(2));

        mockMvc.perform(get("/api/projects/{projectId}/assets/objects", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/projects/{projectId}/dependencies", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("VIEW_REFERENCES_TABLE"));
    }

    @Test
    void missingUploadParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/projects/{projectId}/inputs/files", java.util.UUID.randomUUID())
                        .file(new MockMultipartFile("files", "schema/users.sql", "text/plain", "create table users (id number);".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void editAfterApprovalInvalidatesSqlPackageExport() throws Exception {
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale Gate Pilot",
                                  "description": "Export gate"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectJson = projectResult.getResponse().getContentAsString();
        String projectId = JsonPath.read(projectJson, "$.data.project.id");
        String sourceProjectId = JsonPath.read(projectJson, "$.data.sourceProjects[0].id");

        mockMvc.perform(post("/api/projects/{projectId}/inputs/manual-sql", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceProjectId": "%s",
                                  "name": "table.sql",
                                  "sql": "create table audit_log (id number(10,0));"
                                }
                                """.formatted(sourceProjectId)))
                .andExpect(status().isOk());

        MvcResult versionResult = mockMvc.perform(get("/api/projects/{projectId}/sql-versions", projectId))
                .andExpect(status().isOk())
                .andReturn();
        String versionId = JsonPath.read(versionResult.getResponse().getContentAsString(), "$.data[0].id");

        MvcResult reportResult = mockMvc.perform(post("/api/projects/{projectId}/reports/precheck", projectId))
                .andExpect(status().isOk())
                .andReturn();
        String reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.stageReport.id");

        mockMvc.perform(post("/api/projects/{projectId}/reviews", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": "%s",
                                  "decision": "APPROVED",
                                  "reviewer": "dba",
                                  "comment": "approved"
                                }
                                """.formatted(reportId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/exports/sql-package/preview", projectId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/sql-versions/{versionId}/edits", projectId, versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetSql": "create table \\"audit_log\\" (\\"id\\" numeric(10,0), \\"memo\\" text);"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/exports/sql-package/preview", projectId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Current precheck report")));
    }

    @Test
    void unsupportedStatementsBlockPrecheckReportAndExport() throws Exception {
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Parse Issue Pilot",
                                  "description": "Parse issues"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectJson = projectResult.getResponse().getContentAsString();
        String projectId = JsonPath.read(projectJson, "$.data.project.id");
        String sourceProjectId = JsonPath.read(projectJson, "$.data.sourceProjects[0].id");

        mockMvc.perform(post("/api/projects/{projectId}/inputs/manual-sql", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceProjectId": "%s",
                                  "name": "mixed.sql",
                                  "sql": "create table app_user (id number(8,0)); grant select on app_user to app_role;"
                                }
                                """.formatted(sourceProjectId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/assets/parse-issues", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        MvcResult reportResult = mockMvc.perform(post("/api/projects/{projectId}/reports/precheck", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageReport.status").value("BLOCKED"))
                .andReturn();
        String reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.stageReport.id");

        mockMvc.perform(post("/api/projects/{projectId}/reviews", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": "%s",
                                  "decision": "APPROVED",
                                  "reviewer": "dba",
                                  "comment": "approved"
                                }
                                """.formatted(reportId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Blocked report")));

        mockMvc.perform(get("/api/projects/{projectId}/sql-versions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("BASELINE"))));
    }
}
