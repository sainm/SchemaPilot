package org.sainm.schemapilot.app.ai;

import org.sainm.schemapilot.ai.AiContextBuilder;
import org.sainm.schemapilot.ai.AiProvider;
import org.sainm.schemapilot.ai.AiSuggestionRepository;
import org.sainm.schemapilot.ai.AiSuggestionService;
import org.sainm.schemapilot.ai.InMemoryAiSuggestionRepository;
import org.sainm.schemapilot.ai.MockAiProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AiModuleConfiguration {

    @Bean
    AiContextBuilder aiContextBuilder() {
        return new AiContextBuilder();
    }

    @Bean
    AiProvider aiProvider() {
        return new MockAiProvider();
    }

    @Bean
    AiSuggestionRepository aiSuggestionRepository() {
        return new InMemoryAiSuggestionRepository();
    }

    @Bean
    AiSuggestionService aiSuggestionService(AiProvider aiProvider, AiSuggestionRepository repository) {
        return new AiSuggestionService(aiProvider, repository);
    }
}
