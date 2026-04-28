package org.sainm.schemapilot.migration;

import java.util.Optional;
import java.util.UUID;

interface MigrationPlanRepository {
    MigrationPlan save(MigrationPlan plan);

    Optional<MigrationPlan> findById(UUID id);
}
