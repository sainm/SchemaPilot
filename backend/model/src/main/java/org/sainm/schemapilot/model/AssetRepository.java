package org.sainm.schemapilot.model;

import java.util.List;
import java.util.UUID;

public interface AssetRepository {

    void replaceInputSourceAssets(UUID inputSourceId, List<DbObject> objects, List<ParseIssue> parseIssues);

    List<DbObject> findObjects(UUID projectId);

    List<DbObject> findObjects(UUID projectId, UUID sourceProjectId);

    List<ParseIssue> findParseIssues(UUID projectId);
}
