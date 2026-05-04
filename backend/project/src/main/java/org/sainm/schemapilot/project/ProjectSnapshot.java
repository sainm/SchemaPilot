package org.sainm.schemapilot.project;

import java.util.List;

public record ProjectSnapshot(
        MigrationProject project,
        List<SourceProject> sourceProjects) {

    public ProjectSnapshot {
        sourceProjects = List.copyOf(sourceProjects);
    }
}
