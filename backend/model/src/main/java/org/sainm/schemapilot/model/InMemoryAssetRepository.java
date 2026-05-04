package org.sainm.schemapilot.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAssetRepository implements AssetRepository {

    private final ConcurrentMap<UUID, DbObject> objects = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ParseIssue> parseIssues = new ConcurrentHashMap<>();

    @Override
    public void replaceInputSourceAssets(UUID inputSourceId, List<DbObject> newObjects, List<ParseIssue> newParseIssues) {
        objects.entrySet().removeIf(entry -> entry.getValue().inputSourceId().equals(inputSourceId));
        parseIssues.entrySet().removeIf(entry -> entry.getValue().inputSourceId().equals(inputSourceId));
        newObjects.forEach(object -> objects.put(object.id(), object));
        newParseIssues.forEach(issue -> parseIssues.put(issue.id(), issue));
    }

    @Override
    public List<DbObject> findObjects(UUID projectId) {
        return objects.values().stream()
                .filter(object -> object.projectId().equals(projectId))
                .sorted(Comparator.comparing((DbObject object) -> object.type().name()).thenComparing(DbObject::name))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public List<DbObject> findObjects(UUID projectId, UUID sourceProjectId) {
        return findObjects(projectId).stream()
                .filter(object -> object.sourceProjectId().equals(sourceProjectId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public List<ParseIssue> findParseIssues(UUID projectId) {
        return parseIssues.values().stream()
                .filter(issue -> issue.projectId().equals(projectId))
                .sorted(Comparator.comparing(issue -> issue.sourceLocation().startOffset()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
