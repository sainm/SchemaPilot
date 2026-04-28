package org.sainm.schemapilot.workbench;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedAiSuggestion(
        UUID id,
        UUID snapshotId,
        int statementIndex,
        String provider,
        String model,
        String promptVersion,
        String inputHash,
        String suggestion,
        List<String> evidence,
        List<String> citedChunkKeys,
        AiSuggestionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    SavedAiSuggestion withStatus(AiSuggestionStatus nextStatus) {
        return new SavedAiSuggestion(
                id,
                snapshotId,
                statementIndex,
                provider,
                model,
                promptVersion,
                inputHash,
                suggestion,
                evidence,
                citedChunkKeys,
                nextStatus,
                createdAt,
                Instant.now()
        );
    }
}
