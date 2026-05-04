package org.sainm.schemapilot.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ProjectControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createProjectReturnsProjectAndDefaultSourceProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pilot",
                                  "description": "Oracle migration assessment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.project.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sourceProjects[0].type").value("MANUAL_BATCH"));
    }

    @Test
    void createSourceProjectReturnsUpdatedProjectSnapshot() throws Exception {
        String projectJson = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pilot",
                                  "description": "Oracle migration assessment"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = com.jayway.jsonpath.JsonPath.read(projectJson, "$.data.project.id");

        mockMvc.perform(post("/api/projects/{projectId}/source-projects", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Billing Export",
                                  "type": "DATABASE_EXPORT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceProjects.length()").value(2))
                .andExpect(jsonPath("$.data.sourceProjects[1].name").value("Billing Export"))
                .andExpect(jsonPath("$.data.sourceProjects[1].type").value("DATABASE_EXPORT"));
    }

    @Test
    void invalidPathVariableReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/projects/not-a-uuid/assets/objects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void invalidJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
