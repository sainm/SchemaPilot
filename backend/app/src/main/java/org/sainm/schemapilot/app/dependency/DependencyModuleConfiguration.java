package org.sainm.schemapilot.app.dependency;

import org.sainm.schemapilot.dependency.DependencyGraphService;
import org.sainm.schemapilot.dependency.DependencyRepository;
import org.sainm.schemapilot.dependency.InMemoryDependencyRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DependencyModuleConfiguration {

    @Bean
    DependencyRepository dependencyRepository() {
        return new InMemoryDependencyRepository();
    }

    @Bean
    DependencyGraphService dependencyGraphService(DependencyRepository dependencyRepository) {
        return new DependencyGraphService(dependencyRepository);
    }
}
