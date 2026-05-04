package org.sainm.schemapilot.app.dependency;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.dependency.DependencyRepository;
import org.sainm.schemapilot.dependency.ObjectDependency;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/dependencies")
class DependencyController {

    private final DependencyRepository dependencyRepository;

    DependencyController(DependencyRepository dependencyRepository) {
        this.dependencyRepository = dependencyRepository;
    }

    @GetMapping
    ApiResponse<List<ObjectDependency>> listDependencies(@PathVariable UUID projectId) {
        return ApiResponse.success(dependencyRepository.findDependencies(projectId));
    }
}
