package org.sainm.schemapilot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    private final ProjectService service = new ProjectService(new InMemoryProjectRepository());

    @Test
    void createProjectCreatesDraftWithDefaultSourceProject() {
        CreateProjectCommand command = new CreateProjectCommand("Pilot", "Oracle migration assessment");

        ProjectSnapshot snapshot = service.createProject(command);

        assertThat(snapshot.project().name()).isEqualTo("Pilot");
        assertThat(snapshot.project().status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(snapshot.sourceProjects()).hasSize(1);
        assertThat(snapshot.sourceProjects().getFirst().type()).isEqualTo(SourceProjectType.MANUAL_BATCH);
    }

    @Test
    void createProjectRejectsBlankName() {
        assertThatThrownBy(() -> service.createProject(new CreateProjectCommand(" ", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project name");
    }

    @Test
    void createSourceProjectAddsNamedEngineeringUnit() {
        ProjectSnapshot snapshot = service.createProject(new CreateProjectCommand("Pilot", "Oracle migration assessment"));

        ProjectSnapshot updated = service.createSourceProject(new CreateSourceProjectCommand(
                snapshot.project().id(),
                "Billing Export",
                SourceProjectType.DATABASE_EXPORT));

        assertThat(updated.sourceProjects()).hasSize(2);
        assertThat(updated.sourceProjects())
                .anySatisfy(sourceProject -> {
                    assertThat(sourceProject.name()).isEqualTo("Billing Export");
                    assertThat(sourceProject.type()).isEqualTo(SourceProjectType.DATABASE_EXPORT);
                });
    }
}
