package org.sainm.schemapilot.project;

import org.sainm.schemapilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ApiResponse<Project> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Project>> list() {
        return ApiResponse.ok(projectService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Project> get(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.ok(projectService.update(id, request));
    }
}
