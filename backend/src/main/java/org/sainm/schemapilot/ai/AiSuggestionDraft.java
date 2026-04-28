package org.sainm.schemapilot.ai;

import java.util.List;

public record AiSuggestionDraft(
        String provider,
        String model,
        String promptVersion,
        String suggestion,
        List<String> evidence,
        List<String> citedChunkKeys
) {
}
