package org.sainm.schemapilot.app.input;

import org.sainm.schemapilot.input.InMemoryInputRepository;
import org.sainm.schemapilot.input.InputImportService;
import org.sainm.schemapilot.input.InputRepository;
import org.sainm.schemapilot.project.ProjectRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InputModuleConfiguration {

    @Bean
    InputRepository inputRepository() {
        return new InMemoryInputRepository();
    }

    @Bean
    InputImportService inputImportService(ProjectRepository projectRepository, InputRepository inputRepository) {
        return new InputImportService(projectRepository, inputRepository);
    }
}
