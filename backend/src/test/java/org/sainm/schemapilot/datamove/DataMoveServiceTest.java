package org.sainm.schemapilot.datamove;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DataMoveServiceTest {
    @Test
    void movesSmallTableAndRecordsRowsThroughputAndMemory() throws Exception {
        var budget = new MemoryBudgetManager(1024, 512, 256);
        var service = new DataMoveService(
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
        var service = new DataMoveService(
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
        var service = new DataMoveService(
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

    private DataMoveJob awaitFinished(DataMoveService service, UUID jobId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var current = service.get(jobId);
            if (current.status() == DataMoveStatus.COMPLETED || current.status() == DataMoveStatus.FAILED) {
                return current;
            }
            Thread.sleep(20);
        }
        return service.get(jobId);
    }

    private record FakeReader(List<TableRow> rows) implements OracleStreamingReader {
        @Override
        public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
            rows.forEach(rowConsumer);
            return rows.size();
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
