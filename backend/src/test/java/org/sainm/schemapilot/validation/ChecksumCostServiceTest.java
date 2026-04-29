package org.sainm.schemapilot.validation;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.datamove.MemoryBudgetManager;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumCostServiceTest {
    @Test
    void benchmarksChecksumCostAndReleasesFfmBuffers() {
        var budget = new MemoryBudgetManager(1_048_576, 1_048_576, 4096);
        var service = new ChecksumCostService(budget);

        var report = service.benchmark(new ChecksumCostRequest(500, 4, 3, 16));

        assertThat(report.rowCount()).isEqualTo(500);
        assertThat(report.shardCount()).isEqualTo(4);
        assertThat(report.rowsPerSecond()).isPositive();
        assertThat(report.estimatedPayloadBytes()).isEqualTo(500L * 4L * 3L * 16L / 4L);
        assertThat(report.offHeapBytesAfter()).isZero();
        assertThat(report.activeArenasAfter()).isZero();
    }
}
