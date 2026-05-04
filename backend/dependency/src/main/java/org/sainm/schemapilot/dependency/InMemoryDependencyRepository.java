package org.sainm.schemapilot.dependency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryDependencyRepository implements DependencyRepository {

    private final ConcurrentMap<UUID, ObjectDependency> dependencies = new ConcurrentHashMap<>();

    @Override
    public void replaceProjectDependencies(UUID projectId, List<ObjectDependency> newDependencies) {
        dependencies.entrySet().removeIf(entry -> entry.getValue().projectId().equals(projectId));
        newDependencies.forEach(dependency -> dependencies.put(dependency.id(), dependency));
    }

    @Override
    public List<ObjectDependency> findDependencies(UUID projectId) {
        return dependencies.values().stream()
                .filter(dependency -> dependency.projectId().equals(projectId))
                .sorted(Comparator.comparing(dependency -> dependency.type().name()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
