package org.sainm.schemapilot.validation;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.datamove.TableRow;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ValidationReportService {
    private final ValidationProbe probe;
    private final ConcurrentHashMap<UUID, ValidationReport> reports = new ConcurrentHashMap<>();

    public ValidationReportService(ValidationProbe probe) {
        this.probe = probe;
    }

    public ValidationReport create(ValidationRequest request) {
        validate(request);
        var checks = new ArrayList<ValidationCheckResult>();
        checks.add(objectExists(request.sourceDataSourceId(), request.sourceTable(), "SOURCE_TABLE"));
        checks.add(objectExists(request.targetDataSourceId(), request.targetTable(), "TARGET_TABLE"));
        checks.add(rowCount(request));
        checks.add(sampleRows(request));
        checks.add(shardChecksum(request));
        safeList(request.views()).forEach(view -> checks.add(viewExecution(request.targetDataSourceId(), view)));
        safeList(request.routines()).forEach(routine -> checks.add(routineCompile(request.targetDataSourceId(), routine)));

        var issues = checks.stream().flatMap(check -> check.issues().stream()).toList();
        var status = issues.isEmpty() ? ValidationStatus.PASSED : ValidationStatus.FAILED;
        var report = new ValidationReport(UUID.randomUUID(), request.sourceDataSourceId(), request.targetDataSourceId(), request.sourceTable(), request.targetTable(), status, List.copyOf(checks), issues, Instant.now());
        reports.put(report.id(), report);
        return report;
    }

    public ValidationReport get(UUID reportId) {
        var report = reports.get(reportId);
        if (report == null) {
            throw new NotFoundException("Validation report not found: " + reportId);
        }
        return report;
    }

    private ValidationCheckResult objectExists(UUID dataSourceId, String objectName, String label) {
        var exists = probe.objectExists(dataSourceId, objectName);
        if (exists) {
            return ValidationCheckResult.passed(ValidationCheckType.OBJECT_EXISTS, "exists", "exists", label + " exists: " + objectName);
        }
        return ValidationCheckResult.failed(
                ValidationCheckType.OBJECT_EXISTS,
                "exists",
                "missing",
                label + " missing: " + objectName,
                new ValidationIssue("BLOCKER", label + "_MISSING", objectName, null, label + " does not exist or is not readable.")
        );
    }

    private ValidationCheckResult rowCount(ValidationRequest request) {
        var sourceRows = probe.rowCount(request.sourceDataSourceId(), request.sourceTable());
        var targetRows = probe.rowCount(request.targetDataSourceId(), request.targetTable());
        if (sourceRows == targetRows) {
            return ValidationCheckResult.passed(ValidationCheckType.ROW_COUNT, Long.toString(sourceRows), Long.toString(targetRows), "Source and target row counts match.");
        }
        return ValidationCheckResult.failed(
                ValidationCheckType.ROW_COUNT,
                Long.toString(sourceRows),
                Long.toString(targetRows),
                "Source and target row counts differ.",
                new ValidationIssue("HIGH", "ROW_COUNT_MISMATCH", request.targetTable(), null, "Expected " + sourceRows + " rows but found " + targetRows + ".")
        );
    }

    private ValidationCheckResult sampleRows(ValidationRequest request) {
        var sampleSize = Math.max(0, request.sampleSize());
        var keyColumn = keyColumn(request);
        var sourceRows = probe.sampleRows(request.sourceDataSourceId(), request.sourceTable(), request.columns(), keyColumn, sampleSize);
        var targetRows = probe.sampleRows(request.targetDataSourceId(), request.targetTable(), request.columns(), keyColumn, sampleSize);
        if (sourceRows.equals(targetRows)) {
            return ValidationCheckResult.passed(ValidationCheckType.SAMPLE_ROWS, Integer.toString(sourceRows.size()), Integer.toString(targetRows.size()), "Sample rows match.");
        }
        return ValidationCheckResult.failed(
                ValidationCheckType.SAMPLE_ROWS,
                fingerprint(sourceRows),
                fingerprint(targetRows),
                "Sample rows differ.",
                new ValidationIssue("HIGH", "SAMPLE_ROWS_MISMATCH", request.targetTable(), null, "Ordered sample rows are different.")
        );
    }

    private ValidationCheckResult shardChecksum(ValidationRequest request) {
        var shardCount = Math.max(1, request.checksumShardCount());
        var keyColumn = keyColumn(request);
        var sourceChecksums = probe.shardChecksums(request.sourceDataSourceId(), request.sourceTable(), request.columns(), keyColumn, shardCount);
        var targetChecksums = probe.shardChecksums(request.targetDataSourceId(), request.targetTable(), request.columns(), keyColumn, shardCount);
        var sourceSorted = sourceChecksums.stream().sorted(Comparator.comparingInt(ShardChecksum::shardIndex)).toList();
        var targetSorted = targetChecksums.stream().sorted(Comparator.comparingInt(ShardChecksum::shardIndex)).toList();
        if (sourceSorted.equals(targetSorted)) {
            return ValidationCheckResult.passed(ValidationCheckType.SHARD_CHECKSUM, checksumSummary(sourceSorted), checksumSummary(targetSorted), "Shard checksums match.");
        }
        var mismatchShard = firstMismatch(sourceSorted, targetSorted);
        return ValidationCheckResult.failed(
                ValidationCheckType.SHARD_CHECKSUM,
                checksumSummary(sourceSorted),
                checksumSummary(targetSorted),
                "Shard checksums differ.",
                new ValidationIssue("HIGH", "SHARD_CHECKSUM_MISMATCH", request.targetTable(), mismatchShard, "Checksum mismatch detected at shard " + mismatchShard + ".")
        );
    }

    private ValidationCheckResult viewExecution(UUID dataSourceId, String viewName) {
        if (probe.viewExecutable(dataSourceId, viewName)) {
            return ValidationCheckResult.passed(ValidationCheckType.VIEW_EXECUTION, "executable", "executable", "View executes: " + viewName);
        }
        return ValidationCheckResult.failed(
                ValidationCheckType.VIEW_EXECUTION,
                "executable",
                "failed",
                "View execution failed: " + viewName,
                new ValidationIssue("MEDIUM", "VIEW_EXECUTION_FAILED", viewName, null, "View cannot be executed.")
        );
    }

    private ValidationCheckResult routineCompile(UUID dataSourceId, String routineName) {
        if (probe.routineCompiles(dataSourceId, routineName)) {
            return ValidationCheckResult.passed(ValidationCheckType.ROUTINE_COMPILE, "compiled", "compiled", "Routine compiles: " + routineName);
        }
        return ValidationCheckResult.failed(
                ValidationCheckType.ROUTINE_COMPILE,
                "compiled",
                "failed",
                "Routine compile/existence check failed: " + routineName,
                new ValidationIssue("MEDIUM", "ROUTINE_COMPILE_FAILED", routineName, null, "Routine is missing or invalid.")
        );
    }

    private void validate(ValidationRequest request) {
        if (request.columns() == null || request.columns().isEmpty()) {
            throw new BadRequestException("Validation requires at least one column.");
        }
        if (request.sampleSize() < 0) {
            throw new BadRequestException("Sample size must be greater than or equal to zero.");
        }
        if (request.checksumShardCount() <= 0) {
            throw new BadRequestException("Checksum shard count must be greater than zero.");
        }
    }

    private String keyColumn(ValidationRequest request) {
        return request.keyColumn() == null || request.keyColumn().isBlank() ? request.columns().get(0) : request.keyColumn();
    }

    private String fingerprint(List<TableRow> rows) {
        return Integer.toHexString(rows.hashCode());
    }

    private String checksumSummary(List<ShardChecksum> checksums) {
        return checksums.stream()
                .map(checksum -> checksum.shardIndex() + ":" + checksum.rows() + ":" + checksum.checksum())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private int firstMismatch(List<ShardChecksum> source, List<ShardChecksum> target) {
        var max = Math.max(source.size(), target.size());
        for (int i = 0; i < max; i++) {
            if (i >= source.size() || i >= target.size() || !source.get(i).equals(target.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
