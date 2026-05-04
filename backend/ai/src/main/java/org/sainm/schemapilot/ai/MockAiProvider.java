package org.sainm.schemapilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.risk.ObjectRiskIssue;
import org.sainm.schemapilot.rule.RuleHit;

public class MockAiProvider implements AiProvider {

    @Override
    public String name() {
        return "mock-ai";
    }

    @Override
    public AiSuggestion suggest(AiSuggestionType type, AiContext context) {
        List<RuleHit> ruleHits = context.risks().stream()
                .map(ObjectRiskIssue::ruleHit)
                .toList();
        List<UUID> affectedObjects = context.risks().stream()
                .map(ObjectRiskIssue::objectId)
                .distinct()
                .toList();
        return new AiSuggestion(
                UUID.randomUUID(),
                context.projectId(),
                type,
                AiSuggestionStatus.DRAFT,
                title(type),
                "Detected %d objects, %d dependencies, %d risks and %d conversion results."
                        .formatted(context.objects().size(), context.dependencies().size(), context.risks().size(), context.conversions().size()),
                ruleHits,
                affectedObjects,
                List.of(
                        "Review HIGH/BLOCKER risks first.",
                        "Apply deterministic rules before manual edits.",
                        "Keep edited SQL as a new version instead of overwriting generated SQL."),
                List.of(
                        "Run PostgreSQL syntax precheck.",
                        "Validate converted object dependencies.",
                        "Regenerate precheck report before approval."),
                context.risks().isEmpty()
                        ? List.of("No risks were available in context; suggestion is limited to current assets.")
                        : List.of("This is a deterministic mock suggestion; human approval is still required."),
                Instant.now());
    }

    private String title(AiSuggestionType type) {
        return switch (type) {
            case RISK_EXPLANATION -> "Risk explanation draft";
            case SQL_REWRITE -> "SQL rewrite draft";
            case VALIDATION_PLAN -> "Validation plan draft";
            case REPORT_SUMMARY -> "Precheck report summary draft";
        };
    }
}
