package org.sainm.schemapilot.knowledge;

import java.util.List;
import java.util.Map;

public record KnowledgeMultiRecallResponse(
        List<KnowledgeSearchResult> results,
        Map<String, Integer> channelHits
) {
}
