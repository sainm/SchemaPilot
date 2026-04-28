package org.sainm.schemapilot.project;

import org.sainm.schemapilot.common.api.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectServiceTest {
    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectService service = new ProjectService(repository);

    @Test
    void createsDraftProject() {
        var project = service.create(new CreateProjectRequest("  Pilot  ", "  First loop  "));

        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo("Pilot");
        assertThat(project.description()).isEqualTo("First loop");
        assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(project.createdAt()).isNotNull();
        assertThat(project.updatedAt()).isNotNull();
    }

    @Test
    void throwsWhenProjectDoesNotExist() {
        var id = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    private static class InMemoryProjectRepository implements ProjectRepository {
        private final List<Project> projects = new ArrayList<>();

        @Override
        public Project save(Project project) {
            projects.add(project);
            return project;
        }

        @Override
        public Optional<Project> findById(UUID id) {
            return projects.stream()
                    .filter(project -> project.id().equals(id))
                    .findFirst();
        }

        @Override
        public List<Project> findAll() {
            return List.copyOf(projects);
        }

        @Override
        public Project update(Project project) {
            projects.removeIf(existing -> existing.id().equals(project.id()));
            projects.add(project);
            return project;
        }
    }
}
