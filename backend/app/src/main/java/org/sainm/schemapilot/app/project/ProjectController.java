package org.sainm.schemapilot.app.project;

import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.project.CreateProjectCommand;
import org.sainm.schemapilot.project.CreateSourceProjectCommand;
import org.sainm.schemapilot.project.ProjectService;
import org.sainm.schemapilot.project.ProjectSnapshot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectService projectService;

    ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    ApiResponse<ProjectSnapshot> createProject(@RequestBody CreateProjectRequest request) {
        ProjectSnapshot snapshot = projectService.createProject(new CreateProjectCommand(request.name(), request.description()));
        return ApiResponse.success(snapshot);
    }

    @PostMapping("/{projectId}/source-projects")
    ApiResponse<ProjectSnapshot> createSourceProject(
            @PathVariable UUID projectId,
            @RequestBody CreateSourceProjectRequest request) {
        ProjectSnapshot snapshot = projectService.createSourceProject(new CreateSourceProjectCommand(
                projectId,
                request.name(),
                request.type()));
        return ApiResponse.success(snapshot);
    }
}
