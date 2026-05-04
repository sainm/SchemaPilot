package org.sainm.schemapilot.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    void saveProject(MigrationProject project);

    void saveSourceProject(SourceProject sourceProject);

    Optional<MigrationProject> findProject(UUID projectId);

    List<SourceProject> findSourceProjects(UUID projectId);
}
