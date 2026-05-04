package org.sainm.schemapilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AiSuggestionServiceTest {

    private final InMemoryAiSuggestionRepository repository = new InMemoryAiSuggestionRepository();
    private final AiSuggestionService service = new AiSuggestionService(new MockAiProvider(), repository);

    @Test
    void createsStructuredSuggestionAndCallLog() {
        UUID projectId = UUID.randomUUID();

        AiSuggestion suggestion = service.createSuggestion(
                AiSuggestionType.REPORT_SUMMARY,
                new AiContext(projectId, List.of(), List.of(), List.of(), List.of()));

        assertThat(suggestion.ruleHits()).isEmpty();
        assertThat(suggestion.changePlan()).isNotEmpty();
        assertThat(suggestion.validationPlan()).isNotEmpty();
        assertThat(repository.findSuggestions(projectId)).hasSize(1);
        assertThat(repository.findCallLogs(projectId)).hasSize(1);
    }
}
