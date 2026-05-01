package org.sainm.schemapilot.datamove;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.sql.SqlIdentifierValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DataMoveServiceTest {
    @Test
    void movesSmallTableAndRecordsRowsThroughputAndMemory() throws Exception {
        var budget = new MemoryBudgetManager(1024, 512, 256);
        var service = service(
                new FakeReader(List.of(new TableRow(List.of("1", "alice")), new TableRow(List.of("2", "bob")))),
                new FakeWriter(false, -1),
                budget
        );

        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 2));
        var completed = awaitFinished(service, job.id());

        assertThat(completed.status()).isEqualTo(DataMoveStatus.COMPLETED);
        assertThat(completed.rowsRead()).isEqualTo(2);
        assertThat(completed.rowsWritten()).isEqualTo(2);
        assertThat(completed.rowsPerSecond()).isPositive();
        assertThat(completed.offHeapBytes()).isZero();
        assertThat(completed.activeArenas()).isZero();
    }

    @Test
    void copyFailureCreatesWorkItemAndCanRetry() throws Exception {
        var writer = new FakeWriter(true, -1);
        var service = service(
                new FakeReader(List.of(new TableRow(List.of("1", "alice")))),
                writer,
                new MemoryBudgetManager(1024, 512, 256)
        );

        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 1));
        var failed = awaitFinished(service, job.id());

        assertThat(failed.status()).isEqualTo(DataMoveStatus.FAILED);
        assertThat(failed.workItem()).startsWith("COPY_WORK_ITEM:users");
        assertThat(failed.errorMessage()).contains("copy failed");

        writer.fail = false;
        service.retry(job.id());
        var completed = awaitFinished(service, job.id());

        assertThat(completed.status()).isEqualTo(DataMoveStatus.COMPLETED);
        assertThat(completed.attempts()).isEqualTo(2);
    }

    @Test
    void rowCountMismatchCreatesValidationWorkItem() throws Exception {
        var service = service(
                new FakeReader(List.of(new TableRow(List.of("1", "alice")))),
                new FakeWriter(false, 0),
                new MemoryBudgetManager(1024, 512, 256)
        );

        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 1));
        var failed = awaitFinished(service, job.id());

        assertThat(failed.status()).isEqualTo(DataMoveStatus.FAILED);
        assertThat(failed.workItem()).startsWith("ROW_COUNT_WORK_ITEM:users");
        assertThat(failed.errorMessage()).contains("Row count validation failed");
    }

    @Test
    void largeTableUsesRangeShardsAndCheckpointsCompletedShards() throws Exception {
        var rows = List.of(
                new TableRow(List.of("1", "alice")),
                new TableRow(List.of("2", "bob")),
                new TableRow(List.of("3", "carol")),
                new TableRow(List.of("4", "dave"))
        );
        var service = service(new ShardedFakeReader(rows, new NumericBounds(1L, 4L)), new FakeWriter(false, -1), new MemoryBudgetManager(4096, 2048, 1024));

        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 4, 4, 2, 2, "ID", 4, 4, 1, 0));
        var completed = awaitFinished(service, job.id());

        assertThat(completed.status()).isEqualTo(DataMoveStatus.COMPLETED);
        assertThat(completed.largeTable()).isTrue();
        assertThat(completed.shardStrategy()).isEqualTo(DataMoveShardStrategy.RANGE);
        assertThat(completed.totalShards()).isEqualTo(2);
        assertThat(completed.completedShards()).isEqualTo(2);
        assertThat(completed.checkpoint()).startsWith("RANGE:1");
    }

    @Test
    void largeTableFallsBackToHashShardsWhenBoundsAreUnknown() throws Exception {
        var rows = List.of(
                new TableRow(List.of("1", "alice")),
                new TableRow(List.of("2", "bob")),
                new TableRow(List.of("3", "carol"))
        );
        var service = service(new ShardedFakeReader(rows, NumericBounds.unknown()), new FakeWriter(false, -1), new MemoryBudgetManager(4096, 2048, 1024));

        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 9, 9, 2, 3, "ID", 4, 4, 1, 0));
        var completed = awaitFinished(service, job.id());

        assertThat(completed.status()).isEqualTo(DataMoveStatus.COMPLETED);
        assertThat(completed.shardStrategy()).isEqualTo(DataMoveShardStrategy.HASH);
        assertThat(completed.totalShards()).isEqualTo(3);
        assertThat(completed.completedShards()).isEqualTo(3);
    }

    @Test
    void canPauseResumeAndCancelLongRunningMove() throws Exception {
        var rows = new ArrayList<TableRow>();
        for (int i = 0; i < 20; i++) {
            rows.add(new TableRow(List.of(Integer.toString(i), "name-" + i)));
        }
        var service = service(new SlowFakeReader(rows), new FakeWriter(false, -1), new MemoryBudgetManager(8192, 4096, 1024));
        var job = service.start(new DataMoveRequest(UUID.randomUUID(), UUID.randomUUID(), "USERS", "users", List.of("ID", "NAME"), 20, 20, 2, 20, "ID", 4, 4, 1, 5));

        awaitRunning(service, job.id());
        var paused = service.pause(job.id());
        assertThat(paused.status()).isEqualTo(DataMoveStatus.PAUSED);

        service.resume(job.id());
        awaitRunning(service, job.id());
        var cancelled = service.cancel(job.id());
        assertThat(cancelled.status()).isEqualTo(DataMoveStatus.CANCELLED);
        assertThat(awaitFinished(service, job.id()).status()).isEqualTo(DataMoveStatus.CANCELLED);
    }

    private DataMoveService service(OracleStreamingReader reader, PostgresCopyWriter writer, MemoryBudgetManager budget) {
        return new DataMoveService(reader, writer, budget, new DataMoveShardPlanner(new SqlIdentifierValidator()), new DataMoveConcurrencyLimiter());
    }

    private DataMoveJob awaitFinished(DataMoveService service, UUID jobId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            var current = service.get(jobId);
            if (current.status() == DataMoveStatus.COMPLETED || current.status() == DataMoveStatus.FAILED || current.status() == DataMoveStatus.CANCELLED) {
                return current;
            }
            Thread.sleep(20);
        }
        return service.get(jobId);
    }

    private void awaitRunning(DataMoveService service, UUID jobId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var current = service.get(jobId);
            if (current.status() == DataMoveStatus.RUNNING) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private record FakeReader(List<TableRow> rows) implements OracleStreamingReader {
        @Override
        public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
            rows.forEach(rowConsumer);
            return rows.size();
        }
    }

    private record ShardedFakeReader(List<TableRow> rows, NumericBounds bounds) implements OracleStreamingReader {
        @Override
        public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
            rows.forEach(rowConsumer);
            return rows.size();
        }

        @Override
        public long streamShard(UUID sourceDataSourceId, String sourceTable, List<String> columns, DataMoveShard shard, Consumer<TableRow> rowConsumer) {
            var count = 0L;
            for (var row : rowsFor(shard)) {
                rowConsumer.accept(row);
                count++;
            }
            return count;
        }

        @Override
        public NumericBounds numericBounds(UUID sourceDataSourceId, String sourceTable, String shardColumn) {
            return bounds;
        }

        private List<TableRow> rowsFor(DataMoveShard shard) {
            if (shard.strategy() != DataMoveShardStrategy.RANGE) {
                return rows;
            }
            return rows.stream()
                    .filter(row -> {
                        var id = Long.parseLong(row.values().get(0));
                        return id >= shard.lowerBound() && id < shard.upperBound();
                    })
                    .toList();
        }
    }

    private record SlowFakeReader(List<TableRow> rows) implements OracleStreamingReader {
        @Override
        public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
            rows.forEach(row -> {
                rowConsumer.accept(row);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new DataMoveException("interrupted", ex);
                }
            });
            return rows.size();
        }

        @Override
        public long streamShard(UUID sourceDataSourceId, String sourceTable, List<String> columns, DataMoveShard shard, Consumer<TableRow> rowConsumer) {
            return stream(sourceDataSourceId, sourceTable, columns, rowConsumer);
        }
    }

    private static class FakeWriter implements PostgresCopyWriter {
        boolean fail;
        final long forcedCount;

        FakeWriter(boolean fail, long forcedCount) {
            this.fail = fail;
            this.forcedCount = forcedCount;
        }

        @Override
        public long copy(UUID targetDataSourceId, String targetTable, List<String> columns, Iterable<TableRow> rows, Consumer<DataMoveProgress> progressConsumer) {
            if (fail) {
                throw new DataMoveException("copy failed at batch 0", new RuntimeException("boom"));
            }
            var count = 0L;
            for (var ignored : rows) {
                count++;
                progressConsumer.accept(new DataMoveProgress(count, count));
            }
            return forcedCount >= 0 ? forcedCount : count;
        }
    }
}
