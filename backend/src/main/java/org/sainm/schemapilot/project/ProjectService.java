package org.sainm.schemapilot.project;

import org.sainm.schemapilot.common.api.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project create(CreateProjectRequest request) {
        var now = OffsetDateTime.now();
        var project = new Project(
                UUID.randomUUID(),
                request.name().trim(),
                normalizeDescription(request.description()),
                ProjectStatus.DRAFT,
                now,
                now
        );
        return projectRepository.save(project);
    }

    public List<Project> list() {
        return projectRepository.findAll();
    }

    public Project get(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    public Project update(UUID id, UpdateProjectRequest request) {
        var current = get(id);
        var updated = new Project(
                current.id(),
                request.name().trim(),
                normalizeDescription(request.description()),
                current.status(),
                current.createdAt(),
                OffsetDateTime.now()
        );
        return projectRepository.update(updated);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
