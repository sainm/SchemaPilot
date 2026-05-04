package org.sainm.schemapilot.dependency;

import java.util.List;
import java.util.UUID;

public interface DependencyRepository {

    void replaceProjectDependencies(UUID projectId, List<ObjectDependency> dependencies);

    List<ObjectDependency> findDependencies(UUID projectId);
}
