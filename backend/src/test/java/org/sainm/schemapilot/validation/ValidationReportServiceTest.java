package org.sainm.schemapilot.validation;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.datamove.MemoryBudgetManager;
import org.sainm.schemapilot.datamove.TableRow;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationReportServiceTest {
    @Test
    void createsPassingValidationReport() {
        var sourceId = UUID.randomUUID();
        var targetId = UUID.randomUUID();
        var probe = new FakeProbe(sourceId, targetId)
                .sourceRows(List.of(row("1", "alice"), row("2", "bob")))
                .targetRows(List.of(row("1", "alice"), row("2", "bob")))
                .viewExecutable(true)
                .routineCompiles(true);
        var service = new ValidationReportService(probe);

        var report = service.create(request(sourceId, targetId));

        assertThat(report.status()).isEqualTo(ValidationStatus.PASSED);
        assertThat(report.issues()).isEmpty();
        assertThat(report.checks()).extracting(ValidationCheckResult::type)
                .contains(
                        ValidationCheckType.OBJECT_EXISTS,
                        ValidationCheckType.ROW_COUNT,
                        ValidationCheckType.SAMPLE_ROWS,
                        ValidationCheckType.SHARD_CHECKSUM,
                        ValidationCheckType.VIEW_EXECUTION,
                        ValidationCheckType.ROUTINE_COMPILE
                );
        assertThat(service.get(report.id())).isEqualTo(report);
    }

    @Test
    void reportsRowCountSampleChecksumViewAndRoutineFailures() {
        var sourceId = UUID.randomUUID();
        var targetId = UUID.randomUUID();
        var probe = new FakeProbe(sourceId, targetId)
                .sourceRows(List.of(row("1", "alice"), row("2", "bob")))
                .targetRows(List.of(row("1", "alice"), row("2", "bobby"), row("3", "carol")))
                .viewExecutable(false)
                .routineCompiles(false);
        var service = new ValidationReportService(probe);

        var report = service.create(request(sourceId, targetId));

        assertThat(report.status()).isEqualTo(ValidationStatus.FAILED);
        assertThat(report.issues()).extracting(ValidationIssue::code)
                .contains(
                        "ROW_COUNT_MISMATCH",
                        "SAMPLE_ROWS_MISMATCH",
                        "SHARD_CHECKSUM_MISMATCH",
                        "VIEW_EXECUTION_FAILED",
                        "ROUTINE_COMPILE_FAILED"
                );
    }

    @Test
    void ffmChecksumBufferReleasesOffHeapMemory() {
        var budget = new MemoryBudgetManager(4096, 4096, 1024);

        try (var buffer = new FfmChecksumBuffer(budget, 1024)) {
            buffer.appendRow(List.of("1", "alice"));
            buffer.appendRow(List.of("2", "bob"));
            assertThat(buffer.value()).isNotZero();
            assertThat(budget.activeLeases()).isEqualTo(1);
        }

        assertThat(budget.reservedBytes()).isZero();
        assertThat(budget.activeLeases()).isZero();
    }

    private ValidationRequest request(UUID sourceId, UUID targetId) {
        return new ValidationRequest(sourceId, targetId, "USERS", "users", List.of("ID", "NAME"), "ID", 2, 2, List.of("user_view"), List.of("sync_user"));
    }

    private TableRow row(String id, String name) {
        return new TableRow(List.of(id, name));
    }

    private static class FakeProbe implements ValidationProbe {
        private final UUID sourceId;
        private final UUID targetId;
        private List<TableRow> sourceRows = List.of();
        private List<TableRow> targetRows = List.of();
        private boolean viewExecutable;
        private boolean routineCompiles;

        FakeProbe(UUID sourceId, UUID targetId) {
            this.sourceId = sourceId;
            this.targetId = targetId;
        }

        FakeProbe sourceRows(List<TableRow> sourceRows) {
            this.sourceRows = sourceRows;
            return this;
        }

        FakeProbe targetRows(List<TableRow> targetRows) {
            this.targetRows = targetRows;
            return this;
        }

        FakeProbe viewExecutable(boolean viewExecutable) {
            this.viewExecutable = viewExecutable;
            return this;
        }

        FakeProbe routineCompiles(boolean routineCompiles) {
            this.routineCompiles = routineCompiles;
            return this;
        }

        @Override
        public boolean objectExists(UUID dataSourceId, String objectName) {
            return true;
        }

        @Override
        public long rowCount(UUID dataSourceId, String tableName) {
            return rows(dataSourceId).size();
        }

        @Override
        public List<TableRow> sampleRows(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int limit) {
            return rows(dataSourceId).stream().limit(limit).toList();
        }

        @Override
        public List<ShardChecksum> shardChecksums(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int shardCount) {
            var checksums = new long[shardCount];
            var counts = new long[shardCount];
            for (var row : rows(dataSourceId)) {
                var shard = Math.floorMod(row.values().get(0).hashCode(), shardCount);
                checksums[shard] = checksums[shard] * 31 + row.hashCode();
                counts[shard]++;
            }
            var result = new java.util.ArrayList<ShardChecksum>();
            for (int i = 0; i < shardCount; i++) {
                result.add(new ShardChecksum(i, counts[i], checksums[i]));
            }
            return result;
        }

        @Override
        public boolean viewExecutable(UUID dataSourceId, String viewName) {
            return viewExecutable;
        }

        @Override
        public boolean routineCompiles(UUID dataSourceId, String routineName) {
            return routineCompiles;
        }

        private List<TableRow> rows(UUID dataSourceId) {
            if (dataSourceId.equals(sourceId)) {
                return sourceRows;
            }
            if (dataSourceId.equals(targetId)) {
                return targetRows;
            }
            return List.of();
        }
    }
}
