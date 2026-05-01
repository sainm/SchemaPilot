package org.sainm.schemapilot.datamove;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DataMoveService {
    private final OracleStreamingReader reader;
    private final PostgresCopyWriter writer;
    private final MemoryBudgetManager memoryBudgetManager;
    private final DataMoveShardPlanner shardPlanner;
    private final DataMoveConcurrencyLimiter concurrencyLimiter;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, DataMoveJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DataMoveRequest> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<DataMoveShard>> jobShards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DataMoveCheckpoint> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DataMoveControl> controls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public DataMoveService(
            OracleStreamingReader reader,
            PostgresCopyWriter writer,
            MemoryBudgetManager memoryBudgetManager,
            DataMoveShardPlanner shardPlanner,
            DataMoveConcurrencyLimiter concurrencyLimiter
    ) {
        this.reader = reader;
        this.writer = writer;
        this.memoryBudgetManager = memoryBudgetManager;
        this.shardPlanner = shardPlanner;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    public DataMoveJob start(DataMoveRequest request) {
        validate(request);
        var profile = profile(request);
        var shards = shardPlanner.plan(request, profile);
        var now = Instant.now();
        var job = new DataMoveJob(
                UUID.randomUUID(),
                request.sourceDataSourceId(),
                request.targetDataSourceId(),
                request.sourceTable(),
                request.targetTable(),
                List.copyOf(request.columns()),
                DataMoveStatus.QUEUED,
                0,
                0,
                0,
                memoryBudgetManager.reservedBytes(),
                memoryBudgetManager.activeLeases(),
                profile.largeTable(),
                shards.get(0).strategy(),
                shards.size(),
                0,
                DataMoveCheckpoint.start().token(),
                0,
                null,
                null,
                now,
                now
        );
        jobs.put(job.id(), job);
        requests.put(job.id(), request);
        jobShards.put(job.id(), shards);
        checkpoints.put(job.id(), DataMoveCheckpoint.start());
        controls.put(job.id(), new DataMoveControl());
        executor.submit(() -> run(job.id()));
        return job;
    }

    public DataMoveJob get(UUID jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("Data move job not found: " + jobId);
        }
        return job;
    }

    public DataMoveJob retry(UUID jobId) {
        var job = get(jobId);
        if (job.status() != DataMoveStatus.FAILED) {
            throw new BadRequestException("Only failed data move jobs can be retried.");
        }
        controls.put(jobId, new DataMoveControl());
        update(jobId, DataMoveStatus.QUEUED, job.rowsRead(), job.rowsWritten(), job.rowsPerSecond(), job.completedShards(), job.checkpoint(), null, null, false, Instant.now());
        executor.submit(() -> run(jobId));
        return get(jobId);
    }

    public DataMoveJob pause(UUID jobId) {
        var job = get(jobId);
        if (terminal(job.status())) {
            throw new BadRequestException("Finished data move jobs cannot be paused.");
        }
        controls.computeIfAbsent(jobId, ignored -> new DataMoveControl()).pause();
        return update(jobId, DataMoveStatus.PAUSED, job.rowsRead(), job.rowsWritten(), job.rowsPerSecond(), job.completedShards(), job.checkpoint(), null, null, false, Instant.now());
    }

    public DataMoveJob resume(UUID jobId) {
        var job = get(jobId);
        if (job.status() != DataMoveStatus.PAUSED) {
            throw new BadRequestException("Only paused data move jobs can be resumed.");
        }
        controls.computeIfAbsent(jobId, ignored -> new DataMoveControl()).resume();
        return update(jobId, DataMoveStatus.RUNNING, job.rowsRead(), job.rowsWritten(), job.rowsPerSecond(), job.completedShards(), job.checkpoint(), null, null, false, Instant.now());
    }

    public DataMoveJob cancel(UUID jobId) {
        var job = get(jobId);
        if (terminal(job.status())) {
            return job;
        }
        controls.computeIfAbsent(jobId, ignored -> new DataMoveControl()).cancel();
        return update(jobId, DataMoveStatus.CANCELLED, job.rowsRead(), job.rowsWritten(), job.rowsPerSecond(), job.completedShards(), job.checkpoint(), null, null, false, Instant.now());
    }

    public SseEmitter subscribe(UUID jobId) {
        var job = get(jobId);
        var emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(ignored -> remove(jobId, emitter));
        send(emitter, job);
        return emitter;
    }

    private void run(UUID jobId) {
        var request = requests.get(jobId);
        var shards = jobShards.get(jobId);
        var startedAt = Instant.now();
        var checkpoint = checkpoints.getOrDefault(jobId, DataMoveCheckpoint.start());
        var rowsRead = checkpoint.rowsRead();
        var rowsWritten = checkpoint.rowsWritten();
        var completedShards = Math.min(checkpoint.nextShardIndex(), shards.size());
        try (var ignored = concurrencyLimiter.acquire(projectKey(request), tableKey(request), request.projectConcurrencyLimit(), request.globalConcurrencyLimit(), request.tableConcurrencyLimit())) {
            update(jobId, DataMoveStatus.RUNNING, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), completedShards, checkpoint.token(), null, null, true, startedAt);
            for (int shardIndex = checkpoint.nextShardIndex(); shardIndex < shards.size(); shardIndex++) {
                handleControl(jobId, startedAt);
                var shard = shards.get(shardIndex).running();
                var result = runShard(jobId, request, shard, startedAt, rowsRead, rowsWritten, completedShards);
                rowsRead = result.rowsRead();
                rowsWritten = result.rowsWritten();
                completedShards++;
                checkpoint = checkpoint.advance(result.shard(), rowsRead, rowsWritten);
                checkpoints.put(jobId, checkpoint);
                update(jobId, DataMoveStatus.RUNNING, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), completedShards, checkpoint.token(), null, null, false, startedAt);
            }
            if (request.expectedRows() >= 0 && rowsWritten != request.expectedRows()) {
                var workItem = "ROW_COUNT_WORK_ITEM:" + request.targetTable() + ":expected=" + request.expectedRows() + ":actual=" + rowsWritten;
                update(jobId, DataMoveStatus.FAILED, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), completedShards, checkpoint.token(), "Row count validation failed.", workItem, false, startedAt);
                return;
            }
            update(jobId, DataMoveStatus.COMPLETED, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), completedShards, checkpoint.token(), null, null, false, startedAt);
        } catch (CancellationException ex) {
            var current = get(jobId);
            update(jobId, DataMoveStatus.CANCELLED, current.rowsRead(), current.rowsWritten(), current.rowsPerSecond(), current.completedShards(), current.checkpoint(), null, null, false, startedAt);
        } catch (Exception ex) {
            var current = get(jobId);
            var workItem = "COPY_WORK_ITEM:" + current.targetTable() + ":checkpoint=" + current.checkpoint();
            update(jobId, DataMoveStatus.FAILED, current.rowsRead(), current.rowsWritten(), current.rowsPerSecond(), current.completedShards(), current.checkpoint(), ex.getMessage(), workItem, false, startedAt);
        }
    }

    private ShardRunResult runShard(UUID jobId, DataMoveRequest request, DataMoveShard shard, Instant startedAt, long baseRowsRead, long baseRowsWritten, int completedShards) {
        var rows = new ArrayList<TableRow>();
        var rowsReadInShard = reader.streamShard(request.sourceDataSourceId(), request.sourceTable(), request.columns(), shard, row -> {
            handleControl(jobId, startedAt);
            rows.add(row);
            var totalRowsRead = baseRowsRead + rows.size();
            update(jobId, DataMoveStatus.RUNNING, totalRowsRead, baseRowsWritten, rowsPerSecond(startedAt, totalRowsRead), completedShards, shard.withProgress(rows.size(), 0, DataMoveShardStatus.RUNNING).checkpoint(), null, null, false, startedAt);
            rateLimit(startedAt, totalRowsRead, request.rateLimitRowsPerSecond());
        });
        var rowsWrittenInShard = writer.copy(request.targetDataSourceId(), request.targetTable(), request.columns(), rows, progress -> {
            handleControl(jobId, startedAt);
            var totalRowsWritten = baseRowsWritten + progress.rowsWritten();
            update(jobId, DataMoveStatus.RUNNING, baseRowsRead + rowsReadInShard, totalRowsWritten, rowsPerSecond(startedAt, totalRowsWritten), completedShards, shard.withProgress(rowsReadInShard, progress.rowsWritten(), DataMoveShardStatus.RUNNING).checkpoint(), null, null, false, startedAt);
            rateLimit(startedAt, totalRowsWritten, request.rateLimitRowsPerSecond());
        });
        var completed = shard.completed(rowsReadInShard, rowsWrittenInShard);
        return new ShardRunResult(baseRowsRead + rowsReadInShard, baseRowsWritten + rowsWrittenInShard, completed);
    }

    private DataMoveTableProfile profile(DataMoveRequest request) {
        var estimatedRows = request.estimatedRows() >= 0 ? request.estimatedRows() : reader.estimateRows(request.sourceDataSourceId(), request.sourceTable());
        if (estimatedRows < 0) {
            estimatedRows = Math.max(0, request.expectedRows());
        }
        var largeTableThreshold = Math.max(1, request.largeTableThreshold());
        var largeTable = estimatedRows >= largeTableThreshold;
        var shardColumn = request.shardColumn() == null || request.shardColumn().isBlank() ? request.columns().get(0) : request.shardColumn();
        var bounds = largeTable ? reader.numericBounds(request.sourceDataSourceId(), request.sourceTable(), shardColumn) : NumericBounds.unknown();
        return new DataMoveTableProfile(estimatedRows, largeTable, shardColumn, bounds);
    }

    private void validate(DataMoveRequest request) {
        if (request.columns() == null || request.columns().isEmpty()) {
            throw new BadRequestException("Data move requires at least one column.");
        }
        if (request.shardSize() <= 0) {
            throw new BadRequestException("Shard size must be greater than zero.");
        }
        if (request.largeTableThreshold() <= 0) {
            throw new BadRequestException("Large table threshold must be greater than zero.");
        }
        if (request.projectConcurrencyLimit() <= 0 || request.globalConcurrencyLimit() <= 0 || request.tableConcurrencyLimit() <= 0) {
            throw new BadRequestException("Concurrency limits must be greater than zero.");
        }
    }

    private void handleControl(UUID jobId, Instant startedAt) {
        var control = controls.get(jobId);
        if (control == null) {
            return;
        }
        if (control.cancelled()) {
            throw new CancellationException("Data move job was cancelled.");
        }
        while (control.paused()) {
            var current = get(jobId);
            if (current.status() != DataMoveStatus.PAUSED) {
                update(jobId, DataMoveStatus.PAUSED, current.rowsRead(), current.rowsWritten(), current.rowsPerSecond(), current.completedShards(), current.checkpoint(), null, null, false, startedAt);
            }
            sleep(25);
            if (control.cancelled()) {
                throw new CancellationException("Data move job was cancelled.");
            }
        }
    }

    private void rateLimit(Instant startedAt, long rows, long rowsPerSecondLimit) {
        if (rowsPerSecondLimit <= 0 || rows <= 0) {
            return;
        }
        var expectedMillis = (long) ((rows * 1000.0) / rowsPerSecondLimit);
        var elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        if (expectedMillis > elapsedMillis) {
            sleep(Math.min(100, expectedMillis - elapsedMillis));
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataMoveException("Data move job was interrupted.", ex);
        }
    }

    private DataMoveJob update(
            UUID jobId,
            DataMoveStatus status,
            long rowsRead,
            long rowsWritten,
            double rowsPerSecond,
            int completedShards,
            String checkpoint,
            String errorMessage,
            String workItem,
            boolean incrementAttempts,
            Instant startedAt
    ) {
        var updated = jobs.computeIfPresent(jobId, (ignored, current) -> new DataMoveJob(
                current.id(),
                current.sourceDataSourceId(),
                current.targetDataSourceId(),
                current.sourceTable(),
                current.targetTable(),
                current.columns(),
                status,
                rowsRead,
                rowsWritten,
                rowsPerSecond,
                memoryBudgetManager.reservedBytes(),
                memoryBudgetManager.activeLeases(),
                current.largeTable(),
                current.shardStrategy(),
                current.totalShards(),
                completedShards,
                checkpoint,
                incrementAttempts ? current.attempts() + 1 : current.attempts(),
                errorMessage,
                workItem,
                current.createdAt(),
                Instant.now()
        ));
        if (updated != null) {
            publish(updated);
        }
        return updated;
    }

    private double rowsPerSecond(Instant startedAt, long rows) {
        var seconds = Math.max(0.001, Duration.between(startedAt, Instant.now()).toMillis() / 1000.0);
        return rows / seconds;
    }

    private void publish(DataMoveJob job) {
        List.copyOf(subscribers.getOrDefault(job.id(), new CopyOnWriteArrayList<>())).forEach(emitter -> send(emitter, job));
        if (terminal(job.status())) {
            subscribers.remove(job.id());
        }
    }

    private void send(SseEmitter emitter, DataMoveJob job) {
        try {
            emitter.send(SseEmitter.event().name("data-move-progress").id(job.id().toString()).data(job));
            if (terminal(job.status())) {
                emitter.complete();
            }
        } catch (IOException ex) {
            remove(job.id(), emitter);
        }
    }

    private boolean terminal(DataMoveStatus status) {
        return status == DataMoveStatus.COMPLETED || status == DataMoveStatus.FAILED || status == DataMoveStatus.CANCELLED;
    }

    private String tableKey(DataMoveRequest request) {
        return request.targetDataSourceId() + ":" + request.targetTable();
    }

    private String projectKey(DataMoveRequest request) {
        return request.targetDataSourceId().toString();
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        var current = subscribers.get(jobId);
        if (current != null) {
            current.remove(emitter);
        }
    }

    private record ShardRunResult(long rowsRead, long rowsWritten, DataMoveShard shard) {
    }
}
