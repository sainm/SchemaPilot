package org.sainm.schemapilot.validation;

import org.sainm.schemapilot.datamove.MemoryBudgetExceededException;
import org.sainm.schemapilot.datamove.MemoryBudgetManager;
import org.sainm.schemapilot.datamove.MemoryLease;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public class FfmChecksumBuffer implements AutoCloseable {
    private final MemoryLease lease;
    private final Arena arena;
    private final MemorySegment segment;
    private final CRC32 crc32 = new CRC32();
    private final long capacity;
    private long position;
    private boolean closed;

    public FfmChecksumBuffer(MemoryBudgetManager budgetManager, long capacity) {
        this.lease = budgetManager.reserveShard(capacity);
        this.arena = Arena.ofConfined();
        this.segment = arena.allocate(capacity);
        this.capacity = capacity;
    }

    public void appendRow(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writeByte((byte) 31);
            }
            var value = values.get(i);
            writeBytes(value == null ? new byte[]{0} : value.getBytes(StandardCharsets.UTF_8));
        }
        writeByte((byte) 30);
    }

    public long value() {
        flush();
        return crc32.getValue();
    }

    private void writeBytes(byte[] bytes) {
        for (byte value : bytes) {
            writeByte(value);
        }
    }

    private void writeByte(byte value) {
        ensureOpen();
        if (position >= capacity) {
            flush();
        }
        if (position >= capacity) {
            throw new MemoryBudgetExceededException("FFM checksum buffer is full and must be flushed.");
        }
        segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, position++, value);
    }

    private void flush() {
        ensureOpen();
        if (position == 0) {
            return;
        }
        crc32.update(segment.asSlice(0, position).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE));
        position = 0;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FFM checksum buffer is already closed.");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            flush();
            closed = true;
            arena.close();
            lease.close();
        }
    }
}
