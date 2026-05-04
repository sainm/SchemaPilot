package org.sainm.schemapilot.convert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SqlVersionRepository {

    void replaceInputSourceGeneratedVersions(UUID inputSourceId, List<SqlVersion> versions);

    void save(SqlVersion version);

    Optional<SqlVersion> findById(UUID id);

    List<SqlVersion> findVersions(UUID projectId);

    int nextVersionNumber(UUID objectId);

    List<SqlVersion> markLatestVersionsBaseline(UUID projectId);
}
