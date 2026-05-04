package org.sainm.schemapilot.convert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryConversionRepository implements ConversionRepository {

    private final ConcurrentMap<UUID, ConversionResult> results = new ConcurrentHashMap<>();

    @Override
    public void replaceInputSourceConversions(UUID inputSourceId, List<ConversionResult> newResults) {
        results.entrySet().removeIf(entry -> entry.getValue().inputSourceId().equals(inputSourceId));
        newResults.forEach(result -> results.put(result.id(), result));
    }

    @Override
    public List<ConversionResult> findConversions(UUID projectId) {
        return results.values().stream()
                .filter(result -> result.projectId().equals(projectId))
                .sorted(Comparator.comparing((ConversionResult result) -> result.level().ordinal())
                        .thenComparing(result -> result.objectId().toString()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
