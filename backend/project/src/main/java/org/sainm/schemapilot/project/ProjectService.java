package org.sainm.schemapilot.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.NotFoundException;

public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public ProjectSnapshot createProject(CreateProjectCommand command) {
        String name = normalizeProjectName(command.name());
        Instant now = Instant.now();
        MigrationProject project = new MigrationProject(
                UUID.randomUUID(),
                name,
                command.description(),
                ProjectStatus.DRAFT,
                now,
                now);
        SourceProject defaultSourceProject = new SourceProject(
                UUID.randomUUID(),
                project.id(),
                "Manual SQL",
                SourceProjectType.MANUAL_BATCH,
                now);

        repository.saveProject(project);
        repository.saveSourceProject(defaultSourceProject);

        return new ProjectSnapshot(project, List.of(defaultSourceProject));
    }

    public ProjectSnapshot createSourceProject(CreateSourceProjectCommand command) {
        MigrationProject project = repository.findProject(command.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        SourceProject sourceProject = new SourceProject(
                UUID.randomUUID(),
                project.id(),
                normalizeSourceProjectName(command.name()),
                command.type() == null ? SourceProjectType.DATABASE_EXPORT : command.type(),
                Instant.now());
        repository.saveSourceProject(sourceProject);
        return new ProjectSnapshot(project, repository.findSourceProjects(project.id()));
    }

    private String normalizeProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name must not be blank");
        }
        return name.trim();
    }

    private String normalizeSourceProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Source project name must not be blank");
        }
        return name.trim();
    }
}
