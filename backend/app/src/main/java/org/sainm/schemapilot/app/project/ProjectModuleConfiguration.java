package org.sainm.schemapilot.app.project;

import org.sainm.schemapilot.project.InMemoryProjectRepository;
import org.sainm.schemapilot.project.ProjectRepository;
import org.sainm.schemapilot.project.ProjectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ProjectModuleConfiguration {

    @Bean
    ProjectRepository projectRepository() {
        return new InMemoryProjectRepository();
    }

    @Bean
    ProjectService projectService(ProjectRepository projectRepository) {
        return new ProjectService(projectRepository);
    }
}
