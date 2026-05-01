package org.sainm.schemapilot.datamove;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

public class FfmLobChunkBuffer implements AutoCloseable {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final MemoryLease lease;
    private final Arena arena;
    private final MemorySegment segment;
    private final long capacity;
    private long position;
    private boolean closed;

    public FfmLobChunkBuffer(MemoryBudgetManager budgetManager, long capacity) {
        this.lease = budgetManager.reserveShard(capacity);
        this.arena = Arena.ofConfined();
        this.segment = arena.allocate(capacity);
        this.capacity = capacity;
    }

    public void appendUtf8(char[] chars, int length, StringBuilder target) {
        var bytes = new String(chars, 0, length).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > capacity) {
            flushUtf8To(target);
            target.append(new String(bytes, StandardCharsets.UTF_8));
            return;
        }
        if (position + bytes.length > capacity) {
            flushUtf8To(target);
        }
        appendBytes(bytes, target, false);
    }

    public void appendHex(byte[] bytes, int length, StringBuilder target) {
        for (int i = 0; i < length; i++) {
            appendByte(bytes[i], target, true);
        }
    }

    public void flushUtf8To(StringBuilder target) {
        ensureOpen();
        if (position == 0) {
            return;
        }
        target.append(new String(segment.asSlice(0, position).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8));
        position = 0;
    }

    public void flushHexTo(StringBuilder target) {
        ensureOpen();
        for (byte value : segment.asSlice(0, position).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE)) {
            target.append(HEX[(value >> 4) & 0x0f]);
            target.append(HEX[value & 0x0f]);
        }
        position = 0;
    }

    public long position() {
        return position;
    }

    private void appendBytes(byte[] bytes, StringBuilder target, boolean hex) {
        for (byte value : bytes) {
            appendByte(value, target, hex);
        }
    }

    private void appendByte(byte value, StringBuilder target, boolean hex) {
        ensureOpen();
        if (position >= capacity) {
            if (hex) {
                flushHexTo(target);
            } else {
                flushUtf8To(target);
            }
        }
        segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, position++, value);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FFM LOB buffer is already closed.");
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
