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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DataMoveService {
    private final OracleStreamingReader reader;
    private final PostgresCopyWriter writer;
    private final MemoryBudgetManager memoryBudgetManager;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, DataMoveJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public DataMoveService(OracleStreamingReader reader, PostgresCopyWriter writer, MemoryBudgetManager memoryBudgetManager) {
        this.reader = reader;
        this.writer = writer;
        this.memoryBudgetManager = memoryBudgetManager;
    }

    public DataMoveJob start(DataMoveRequest request) {
        var now = Instant.now();
        var job = new DataMoveJob(UUID.randomUUID(), request.sourceDataSourceId(), request.targetDataSourceId(), request.sourceTable(), request.targetTable(), List.copyOf(request.columns()), DataMoveStatus.QUEUED, 0, 0, 0, memoryBudgetManager.reservedBytes(), memoryBudgetManager.activeLeases(), 0, null, null, now, now);
        jobs.put(job.id(), job);
        executor.submit(() -> run(job.id(), request.expectedRows()));
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
        update(jobId, DataMoveStatus.QUEUED, job.rowsRead(), job.rowsWritten(), job.rowsPerSecond(), null, null, false, Instant.now());
        executor.submit(() -> run(jobId, job.rowsRead()));
        return job;
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

    private void run(UUID jobId, long expectedRows) {
        var startedAt = Instant.now();
        var job = update(jobId, DataMoveStatus.RUNNING, 0, 0, 0, null, null, true, startedAt);
        var rows = new ArrayList<TableRow>();
        try {
            var rowsRead = reader.stream(job.sourceDataSourceId(), job.sourceTable(), job.columns(), row -> {
                rows.add(row);
                update(jobId, DataMoveStatus.RUNNING, rows.size(), 0, rowsPerSecond(startedAt, rows.size()), null, null, false, startedAt);
            });
            var rowsWritten = writer.copy(job.targetDataSourceId(), job.targetTable(), job.columns(), rows, progress -> update(jobId, DataMoveStatus.RUNNING, rowsRead, progress.rowsWritten(), rowsPerSecond(startedAt, progress.rowsWritten()), null, null, false, startedAt));
            if (expectedRows >= 0 && rowsWritten != expectedRows) {
                var workItem = "ROW_COUNT_WORK_ITEM:" + job.targetTable() + ":expected=" + expectedRows + ":actual=" + rowsWritten;
                update(jobId, DataMoveStatus.FAILED, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), "Row count validation failed.", workItem, false, startedAt);
                return;
            }
            update(jobId, DataMoveStatus.COMPLETED, rowsRead, rowsWritten, rowsPerSecond(startedAt, rowsWritten), null, null, false, startedAt);
        } catch (Exception ex) {
            var current = get(jobId);
            var workItem = "COPY_WORK_ITEM:" + current.targetTable() + ":batch=" + current.rowsWritten();
            update(jobId, DataMoveStatus.FAILED, current.rowsRead(), current.rowsWritten(), current.rowsPerSecond(), ex.getMessage(), workItem, false, startedAt);
        }
    }

    private DataMoveJob update(UUID jobId, DataMoveStatus status, long rowsRead, long rowsWritten, double rowsPerSecond, String errorMessage, String workItem, boolean incrementAttempts, Instant startedAt) {
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
        if (job.status() == DataMoveStatus.COMPLETED || job.status() == DataMoveStatus.FAILED) {
            subscribers.remove(job.id());
        }
    }

    private void send(SseEmitter emitter, DataMoveJob job) {
        try {
            emitter.send(SseEmitter.event().name("data-move-progress").id(job.id().toString()).data(job));
            if (job.status() == DataMoveStatus.COMPLETED || job.status() == DataMoveStatus.FAILED) {
                emitter.complete();
            }
        } catch (IOException ex) {
            remove(job.id(), emitter);
        }
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        var current = subscribers.get(jobId);
        if (current != null) {
            current.remove(emitter);
        }
    }
}
