package org.sainm.schemapilot.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryProjectRepository implements ProjectRepository {

    private final ConcurrentMap<UUID, MigrationProject> projects = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SourceProject> sourceProjects = new ConcurrentHashMap<>();

    @Override
    public void saveProject(MigrationProject project) {
        projects.put(project.id(), project);
    }

    @Override
    public void saveSourceProject(SourceProject sourceProject) {
        sourceProjects.put(sourceProject.id(), sourceProject);
    }

    @Override
    public Optional<MigrationProject> findProject(UUID projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public List<SourceProject> findSourceProjects(UUID projectId) {
        return sourceProjects.values().stream()
                .filter(sourceProject -> sourceProject.projectId().equals(projectId))
                .sorted(Comparator.comparing(SourceProject::createdAt))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
