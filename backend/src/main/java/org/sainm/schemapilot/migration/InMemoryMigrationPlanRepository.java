package org.sainm.schemapilot.migration;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
class InMemoryMigrationPlanRepository implements MigrationPlanRepository {
    private final ConcurrentHashMap<UUID, MigrationPlan> plans = new ConcurrentHashMap<>();

    @Override
    public MigrationPlan save(MigrationPlan plan) {
        plans.put(plan.id(), plan);
        return plan;
    }

    @Override
    public Optional<MigrationPlan> findById(UUID id) {
        return Optional.ofNullable(plans.get(id));
    }
}
