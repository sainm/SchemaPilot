package org.sainm.schemapilot.datamove;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmCopyBufferTest {
    @Test
    void reservesOffHeapBudgetWritesCopyRowAndReleasesArena() throws Exception {
        var budget = new MemoryBudgetManager(1024, 512, 256);
        var output = new ByteArrayOutputStream();

        try (var buffer = new FfmCopyBuffer(budget, 128)) {
            buffer.writeField("alice");
            buffer.writeDelimiter();
            buffer.writeField(null);
            buffer.writeRowEnd();
            assertThat(budget.reservedBytes()).isEqualTo(128);
            assertThat(budget.activeLeases()).isEqualTo(1);
            buffer.flushTo(output);
            assertThat(buffer.position()).isZero();
        }

        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("alice\t\\N\n");
        assertThat(budget.reservedBytes()).isZero();
        assertThat(budget.activeLeases()).isZero();
    }

    @Test
    void rejectsShardReservationOverBudget() {
        var budget = new MemoryBudgetManager(1024, 512, 64);

        assertThatThrownBy(() -> new FfmCopyBuffer(budget, 128))
                .isInstanceOf(MemoryBudgetExceededException.class)
                .hasMessageContaining("budget");
    }

    @Test
    void requiresFlushWhenBufferIsFull() {
        var budget = new MemoryBudgetManager(1024, 512, 8);

        try (var buffer = new FfmCopyBuffer(budget, 8)) {
            assertThatThrownBy(() -> buffer.writeField("012345678"))
                    .isInstanceOf(MemoryBudgetExceededException.class)
                    .hasMessageContaining("must be flushed");
        }
    }
}
