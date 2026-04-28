package org.sainm.schemapilot.migration;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.sainm.schemapilot.datasource.DataSourceKind;
import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.workbench.SavedSqlVersion;
import org.sainm.schemapilot.workbench.WorkbenchService;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MigrationPlanService {
    private final WorkbenchService workbenchService;
    private final DataSourceConfigService dataSourceConfigService;
    private final MigrationPlanRepository repository;

    public MigrationPlanService(WorkbenchService workbenchService, DataSourceConfigService dataSourceConfigService, MigrationPlanRepository repository) {
        this.workbenchService = workbenchService;
        this.dataSourceConfigService = dataSourceConfigService;
        this.repository = repository;
    }

    public MigrationPlan create(CreateMigrationPlanRequest request) {
        if (dataSourceConfigService.kind(request.targetDataSourceId()) != DataSourceKind.POSTGRESQL) {
            throw new BadRequestException("Migration plan target datasource must be PostgreSQL.");
        }
        var snapshot = workbenchService.getSnapshot(request.snapshotId());
        var baseline = workbenchService.baselineVersions(request.snapshotId());
        var steps = baseline.stream()
                .sorted(Comparator.comparingInt(version -> orderWeight(objectType(request.snapshotId(), version.statementIndex()))))
                .map(version -> step(request.snapshotId(), version))
                .toList();
        var now = Instant.now();
        return repository.save(new MigrationPlan(
                UUID.randomUUID(),
                request.snapshotId(),
                request.targetDataSourceId(),
                snapshot.reportVersion(),
                MigrationPlanStatus.READY,
                steps,
                List.of("Plan created from approved baseline " + snapshot.reportVersion() + "."),
                now,
                now
        ));
    }

    public MigrationPlan get(UUID planId) {
        return repository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Migration plan not found: " + planId));
    }

    public MigrationPlan execute(UUID planId) {
        var plan = get(planId);
        var steps = new ArrayList<>(plan.steps());
        var log = new ArrayList<>(plan.executionLog());
        var status = MigrationPlanStatus.COMPLETED;
        try (var connection = dataSourceConfigService.openConnection(plan.targetDataSourceId());
             Statement statement = connection.createStatement()) {
            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i).withStatus(MigrationPlanStepStatus.RUNNING, steps.get(i).attempts() + 1, null, null);
                steps.set(i, step);
                try {
                    statement.execute(step.sql());
                    steps.set(i, step.withStatus(MigrationPlanStepStatus.COMPLETED, step.attempts(), null, null));
                    log.add("Step " + step.sequence() + " completed: " + step.objectType() + " " + step.objectName());
                } catch (Exception ex) {
                    var workItem = "DDL_WORK_ITEM:" + step.objectType() + ":" + step.objectName() + ":" + step.sqlVersionId();
                    steps.set(i, step.withStatus(MigrationPlanStepStatus.FAILED, step.attempts(), ex.getMessage(), workItem));
                    log.add("Step " + step.sequence() + " failed: " + ex.getMessage());
                    status = MigrationPlanStatus.FAILED;
                    break;
                }
            }
        } catch (Exception ex) {
            if (!steps.isEmpty() && steps.stream().noneMatch(step -> step.status() == MigrationPlanStepStatus.FAILED)) {
                var first = steps.getFirst();
                steps.set(0, first.withStatus(MigrationPlanStepStatus.FAILED, first.attempts() + 1, ex.getMessage(), "DDL_WORK_ITEM:" + first.objectType() + ":" + first.objectName() + ":" + first.sqlVersionId()));
            }
            log.add("Plan execution failed before or during connection: " + ex.getMessage());
            status = MigrationPlanStatus.FAILED;
        }
        return repository.save(new MigrationPlan(plan.id(), plan.snapshotId(), plan.targetDataSourceId(), plan.reportVersion(), status, List.copyOf(steps), List.copyOf(log), plan.createdAt(), Instant.now()));
    }

    public MigrationPlan retryStep(UUID planId, UUID stepId) {
        var plan = get(planId);
        var step = plan.steps().stream()
                .filter(item -> item.id().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Migration plan step not found: " + stepId));
        if (step.status() != MigrationPlanStepStatus.FAILED) {
            throw new BadRequestException("Only failed steps can be retried.");
        }
        return execute(planId);
    }

    private MigrationPlanStep step(UUID snapshotId, SavedSqlVersion version) {
        var snapshot = workbenchService.getSnapshot(snapshotId);
        var statement = snapshot.analysis().statements().stream()
                .filter(item -> item.index() == version.statementIndex())
                .findFirst()
                .orElseThrow();
        return new MigrationPlanStep(
                UUID.randomUUID(),
                orderWeight(statement.objectType()) * 1000 + statement.index(),
                statement.objectType(),
                statement.objectName(),
                version.id(),
                version.sql(),
                MigrationPlanStepStatus.PENDING,
                0,
                null,
                null,
                Instant.now()
        );
    }

    private ObjectType objectType(UUID snapshotId, int statementIndex) {
        return workbenchService.getSnapshot(snapshotId).analysis().statements().stream()
                .filter(item -> item.index() == statementIndex)
                .map(item -> item.objectType())
                .findFirst()
                .orElse(ObjectType.UNKNOWN);
    }

    private int orderWeight(ObjectType type) {
        return switch (type) {
            case SEQUENCE -> 1;
            case TABLE -> 2;
            case INDEX -> 3;
            case VIEW -> 4;
            case FUNCTION, PROCEDURE, TRIGGER -> 5;
            case PACKAGE, PACKAGE_BODY -> 9;
            default -> 8;
        };
    }
}
