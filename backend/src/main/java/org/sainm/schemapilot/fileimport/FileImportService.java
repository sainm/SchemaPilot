package org.sainm.schemapilot.fileimport;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@Service
public class FileImportService {
    private final ManualSqlAnalysisService analysisService;
    private final ConcurrentHashMap<UUID, FileImportJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sourceSqlByJob = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public FileImportService(ManualSqlAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    public FileImportJob importSqlFile(String fileName, byte[] content, String encoding) {
        if (content.length == 0) {
            throw new BadRequestException("SQL file is empty.");
        }
        var charset = charset(encoding);
        var now = Instant.now();
        var job = new FileImportJob(
                UUID.randomUUID(),
                fileName == null || fileName.isBlank() ? "uploaded.sql" : fileName,
                sha256(content),
                charset.name(),
                content.length,
                FileImportStatus.QUEUED,
                0,
                null,
                null,
                now,
                now
        );
        jobs.put(job.id(), job);
        executor.submit(() -> parse(job.id(), content, charset));
        return job;
    }

    public SseEmitter subscribe(UUID jobId) {
        var job = getJob(jobId);
        var emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeSubscriber(jobId, emitter));
        emitter.onTimeout(() -> removeSubscriber(jobId, emitter));
        emitter.onError(ignored -> removeSubscriber(jobId, emitter));
        send(emitter, job);
        return emitter;
    }

    public FileImportJob getJob(UUID jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("File import job not found: " + jobId);
        }
        return job;
    }

    private void parse(UUID jobId, byte[] content, Charset charset) {
        try {
            update(jobId, FileImportStatus.PARSING, 25, null, null);
            var job = getJob(jobId);
            var sql = isZip(job.fileName(), content)
                    ? unzipSqlSources(content, charset)
                    : new String(content, charset);
            sourceSqlByJob.put(jobId, sql);
            update(jobId, FileImportStatus.PARSING, 60, null, null);
            var analysis = analysisService.analyze(sql);
            update(jobId, FileImportStatus.COMPLETED, 100, analysis, null);
        } catch (Exception ex) {
            update(jobId, FileImportStatus.FAILED, 100, null, ex.getMessage());
        }
    }

    public String sourceSql(UUID jobId) {
        var job = getJob(jobId);
        if (job.status() != FileImportStatus.COMPLETED) {
            throw new BadRequestException("File import job must be completed before creating a workbench snapshot.");
        }
        var sql = sourceSqlByJob.get(jobId);
        if (sql == null || sql.isBlank()) {
            throw new NotFoundException("Original SQL not found for file import job: " + jobId);
        }
        return sql;
    }

    public String combinedSourceSql(List<UUID> jobIds) {
        var uniqueJobIds = validateBatchJobIds(jobIds);
        return uniqueJobIds.stream()
                .map(jobId -> "-- source file: " + safeSqlComment(getJob(jobId).fileName()) + "\n" + sourceSql(jobId))
                .collect(Collectors.joining("\n\n"));
    }

    public String sourceFileSummary(List<UUID> jobIds) {
        var uniqueJobIds = validateBatchJobIds(jobIds);
        return uniqueJobIds.stream()
                .map(jobId -> safeSqlComment(getJob(jobId).fileName()))
                .collect(Collectors.joining(", "));
    }

    private LinkedHashSet<UUID> validateBatchJobIds(List<UUID> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            throw new BadRequestException("At least one file import job is required.");
        }
        var uniqueJobIds = new LinkedHashSet<>(jobIds);
        if (uniqueJobIds.size() != jobIds.size()) {
            throw new BadRequestException("Duplicate file import jobs are not allowed in one batch.");
        }
        return uniqueJobIds;
    }

    private String safeSqlComment(String value) {
        return (value == null || value.isBlank() ? "uploaded.sql" : value)
                .replaceAll("[\\r\\n]+", " ")
                .replace("*/", "* /");
    }

    private boolean isZip(String fileName, byte[] content) {
        return (fileName != null && fileName.toLowerCase().endsWith(".zip"))
                || (content.length >= 4
                && content[0] == 0x50
                && content[1] == 0x4b
                && content[2] == 0x03
                && content[3] == 0x04);
    }

    private String unzipSqlSources(byte[] content, Charset charset) throws IOException {
        var sources = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content), charset)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && isSqlLike(entry.getName())) {
                    var bytes = readEntry(zip);
                    sources.add("-- source file: " + safeSqlComment(entry.getName()) + "\n" + new String(bytes, charset));
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
        if (sources.isEmpty()) {
            throw new BadRequestException("ZIP package does not contain .sql or .txt files.");
        }
        return String.join("\n\n", sources);
    }

    private boolean isSqlLike(String entryName) {
        var normalized = entryName.toLowerCase();
        return normalized.endsWith(".sql") || normalized.endsWith(".txt");
    }

    private byte[] readEntry(ZipInputStream zip) throws IOException {
        var output = new ByteArrayOutputStream();
        zip.transferTo(output);
        return output.toByteArray();
    }

    private void update(UUID jobId, FileImportStatus status, int progressPercent, org.sainm.schemapilot.sql.SqlAnalysisResponse analysis, String error) {
        var updated = jobs.computeIfPresent(jobId, (ignored, current) -> new FileImportJob(
                current.id(),
                current.fileName(),
                current.checksumSha256(),
                current.encoding(),
                current.sizeBytes(),
                status,
                progressPercent,
                analysis == null ? current.analysis() : analysis,
                error,
                current.createdAt(),
                Instant.now()
        ));
        if (updated != null) {
            publish(updated);
        }
    }

    private void publish(FileImportJob job) {
        List.copyOf(subscribers.getOrDefault(job.id(), new CopyOnWriteArrayList<>()))
                .forEach(emitter -> send(emitter, job));
        if (job.status() == FileImportStatus.COMPLETED || job.status() == FileImportStatus.FAILED) {
            subscribers.remove(job.id());
        }
    }

    private void send(SseEmitter emitter, FileImportJob job) {
        try {
            emitter.send(SseEmitter.event()
                    .name("file-import-progress")
                    .id(job.id().toString())
                    .data(job));
            if (job.status() == FileImportStatus.COMPLETED || job.status() == FileImportStatus.FAILED) {
                emitter.complete();
            }
        } catch (IOException ex) {
            removeSubscriber(job.id(), emitter);
        }
    }

    private void removeSubscriber(UUID jobId, SseEmitter emitter) {
        var current = subscribers.get(jobId);
        if (current != null) {
            current.remove(emitter);
        }
    }

    private Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            throw new BadRequestException("Unsupported file encoding: " + encoding);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
