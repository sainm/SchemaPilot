package org.sainm.schemapilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.rule.RuleHit;

public record AiSuggestion(
        UUID id,
        UUID projectId,
        AiSuggestionType type,
        AiSuggestionStatus status,
        String title,
        String summary,
        List<RuleHit> ruleHits,
        List<UUID> affectedObjects,
        List<String> changePlan,
        List<String> validationPlan,
        List<String> uncertainties,
        Instant createdAt) {

    public AiSuggestion {
        ruleHits = List.copyOf(ruleHits);
        affectedObjects = List.copyOf(affectedObjects);
        changePlan = List.copyOf(changePlan);
        validationPlan = List.copyOf(validationPlan);
        uncertainties = List.copyOf(uncertainties);
    }
}
