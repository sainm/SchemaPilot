package org.sainm.schemapilot.app.project;

import org.sainm.schemapilot.project.SourceProjectType;

record CreateSourceProjectRequest(
        String name,
        SourceProjectType type) {
}
