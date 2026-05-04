package org.sainm.schemapilot.ai;

import java.time.Instant;
import java.util.UUID;

public class AiSuggestionService {

    private final AiProvider aiProvider;
    private final AiSuggestionRepository repository;

    public AiSuggestionService(AiProvider aiProvider, AiSuggestionRepository repository) {
        this.aiProvider = aiProvider;
        this.repository = repository;
    }

    public AiSuggestion createSuggestion(AiSuggestionType type, AiContext context) {
        AiSuggestion suggestion = aiProvider.suggest(type, context);
        repository.saveSuggestion(suggestion);
        repository.saveCallLog(new AiCallLog(
                UUID.randomUUID(),
                context.projectId(),
                aiProvider.name(),
                type,
                context.objects().size(),
                context.risks().size(),
                Instant.now()));
        return suggestion;
    }
}
