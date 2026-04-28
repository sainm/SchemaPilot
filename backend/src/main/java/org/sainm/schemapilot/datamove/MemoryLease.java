package org.sainm.schemapilot.datamove;

import java.util.concurrent.atomic.AtomicBoolean;

public class MemoryLease implements AutoCloseable {
    private final long bytes;
    private final MemoryBudgetManager manager;
    private final AtomicBoolean closed = new AtomicBoolean();

    MemoryLease(long bytes, MemoryBudgetManager manager) {
        this.bytes = bytes;
        this.manager = manager;
    }

    public long bytes() {
        return bytes;
    }

    public boolean closed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            manager.release(bytes);
        }
    }
}
