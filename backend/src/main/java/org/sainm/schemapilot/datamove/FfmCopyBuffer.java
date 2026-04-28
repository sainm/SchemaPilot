package org.sainm.schemapilot.datamove;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class FfmCopyBuffer implements AutoCloseable {
    private final MemoryLease lease;
    private final Arena arena;
    private final MemorySegment segment;
    private final long capacity;
    private long position;
    private boolean closed;

    public FfmCopyBuffer(MemoryBudgetManager budgetManager, long capacity) {
        this.lease = budgetManager.reserveShard(capacity);
        this.arena = Arena.ofConfined();
        this.segment = arena.allocate(capacity);
        this.capacity = capacity;
    }

    public void writeField(String value) {
        if (value == null) {
            writeRaw("\\N", StandardCharsets.UTF_8);
            return;
        }
        writeEscaped(value, StandardCharsets.UTF_8);
    }

    public void writeDelimiter() {
        writeByte((byte) '\t');
    }

    public void writeRowEnd() {
        writeByte((byte) '\n');
    }

    public void flushTo(OutputStream outputStream) throws IOException {
        ensureOpen();
        if (position == 0) {
            return;
        }
        outputStream.write(segment.asSlice(0, position).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE));
        position = 0;
    }

    public long position() {
        return position;
    }

    public long capacity() {
        return capacity;
    }

    private void writeEscaped(String value, Charset charset) {
        var bytes = value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .getBytes(charset);
        for (byte valueByte : bytes) {
            writeByte(valueByte);
        }
    }

    private void writeRaw(String value, Charset charset) {
        for (byte valueByte : value.getBytes(charset)) {
            writeByte(valueByte);
        }
    }

    private void writeByte(byte value) {
        ensureOpen();
        if (position >= capacity) {
            throw new MemoryBudgetExceededException("FFM COPY buffer is full and must be flushed.");
        }
        segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, position++, value);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FFM COPY buffer is already closed.");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            arena.close();
            lease.close();
        }
    }
}
