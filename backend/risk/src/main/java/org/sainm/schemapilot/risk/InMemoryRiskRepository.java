package org.sainm.schemapilot.risk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRiskRepository implements RiskRepository {

    private final ConcurrentMap<UUID, ObjectRiskIssue> risks = new ConcurrentHashMap<>();

    @Override
    public void replaceInputSourceRisks(UUID inputSourceId, List<ObjectRiskIssue> newRisks) {
        risks.entrySet().removeIf(entry -> entry.getValue().inputSourceId().equals(inputSourceId));
        newRisks.forEach(risk -> risks.put(risk.id(), risk));
    }

    @Override
    public List<ObjectRiskIssue> findRisks(UUID projectId) {
        return risks.values().stream()
                .filter(risk -> risk.projectId().equals(projectId))
                .sorted(Comparator.comparing((ObjectRiskIssue risk) -> risk.level().ordinal()).reversed()
                        .thenComparing(ObjectRiskIssue::code))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
