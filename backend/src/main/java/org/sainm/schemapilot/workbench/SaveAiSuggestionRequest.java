package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SaveAiSuggestionRequest(
        @NotNull
        UUID snapshotId,
        @Positive
        int statementIndex,
        @NotBlank
        String provider,
        @NotBlank
        String model,
        String promptVersion,
        @NotBlank
        @Size(max = 12000)
        String suggestion,
        List<String> evidence,
        List<String> citedChunkKeys
) {
    public List<String> evidence() {
        return evidence == null ? List.of() : List.copyOf(evidence);
    }

    public List<String> citedChunkKeys() {
        return citedChunkKeys == null ? List.of() : List.copyOf(citedChunkKeys);
    }
}
