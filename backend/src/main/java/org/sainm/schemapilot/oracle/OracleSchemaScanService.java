package org.sainm.schemapilot.oracle;

import org.sainm.schemapilot.common.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class OracleSchemaScanService {
    private final OracleMetadataExtractor extractor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, OracleScanJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public OracleSchemaScanService(OracleMetadataExtractor extractor) {
        this.extractor = extractor;
    }

    public OracleScanJob start(OracleScanRequest request) {
        var now = Instant.now();
        var job = new OracleScanJob(UUID.randomUUID(), request.dataSourceId(), request.normalizedSchema(), OracleScanStatus.QUEUED, 0, "QUEUED", null, null, now, now);
        jobs.put(job.id(), job);
        executor.submit(() -> run(job.id()));
        return job;
    }

    public OracleScanJob get(UUID jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("Oracle scan job not found: " + jobId);
        }
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

    private void run(UUID jobId) {
        var job = get(jobId);
        try {
            update(jobId, OracleScanStatus.SCANNING, 1, "START", null, null);
            var snapshot = extractor.extract(job.dataSourceId(), job.schemaName(), progress -> update(jobId, OracleScanStatus.SCANNING, progress.percent(), progress.stage(), null, null));
            update(jobId, OracleScanStatus.COMPLETED, 100, "COMPLETED", snapshot, null);
        } catch (Exception ex) {
            update(jobId, OracleScanStatus.FAILED, 100, "FAILED", null, ex.getMessage());
        }
    }

    private void update(UUID jobId, OracleScanStatus status, int progressPercent, String stage, OracleScanSnapshot snapshot, String errorMessage) {
        var updated = jobs.computeIfPresent(jobId, (ignored, current) -> new OracleScanJob(
                current.id(),
                current.dataSourceId(),
                current.schemaName(),
                status,
                progressPercent,
                stage,
                snapshot == null ? current.snapshot() : snapshot,
                errorMessage,
                current.createdAt(),
                Instant.now()
        ));
        if (updated != null) {
            publish(updated);
        }
    }

    private void publish(OracleScanJob job) {
        List.copyOf(subscribers.getOrDefault(job.id(), new CopyOnWriteArrayList<>())).forEach(emitter -> send(emitter, job));
        if (job.status() == OracleScanStatus.COMPLETED || job.status() == OracleScanStatus.FAILED) {
            subscribers.remove(job.id());
        }
    }

    private void send(SseEmitter emitter, OracleScanJob job) {
        try {
            emitter.send(SseEmitter.event().name("oracle-scan-progress").id(job.id().toString()).data(job));
            if (job.status() == OracleScanStatus.COMPLETED || job.status() == OracleScanStatus.FAILED) {
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
