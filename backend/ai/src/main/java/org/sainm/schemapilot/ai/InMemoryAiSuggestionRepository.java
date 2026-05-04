package org.sainm.schemapilot.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAiSuggestionRepository implements AiSuggestionRepository {

    private final ConcurrentMap<UUID, AiSuggestion> suggestions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AiCallLog> callLogs = new ConcurrentHashMap<>();

    @Override
    public void saveSuggestion(AiSuggestion suggestion) {
        suggestions.put(suggestion.id(), suggestion);
    }

    @Override
    public void saveCallLog(AiCallLog callLog) {
        callLogs.put(callLog.id(), callLog);
    }

    @Override
    public List<AiSuggestion> findSuggestions(UUID projectId) {
        return suggestions.values().stream()
                .filter(suggestion -> suggestion.projectId().equals(projectId))
                .sorted(Comparator.comparing(AiSuggestion::createdAt).reversed())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public List<AiCallLog> findCallLogs(UUID projectId) {
        return callLogs.values().stream()
                .filter(callLog -> callLog.projectId().equals(projectId))
                .sorted(Comparator.comparing(AiCallLog::createdAt).reversed())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
