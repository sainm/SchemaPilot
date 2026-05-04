package org.sainm.schemapilot.ai;

import java.util.List;
import java.util.UUID;

public interface AiSuggestionRepository {

    void saveSuggestion(AiSuggestion suggestion);

    void saveCallLog(AiCallLog callLog);

    List<AiSuggestion> findSuggestions(UUID projectId);

    List<AiCallLog> findCallLogs(UUID projectId);
}
