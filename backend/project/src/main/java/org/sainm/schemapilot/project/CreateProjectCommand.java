package org.sainm.schemapilot.project;

public record CreateProjectCommand(
        String name,
        String description) {
}
