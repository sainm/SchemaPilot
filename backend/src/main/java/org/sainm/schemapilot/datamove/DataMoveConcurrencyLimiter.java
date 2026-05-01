package org.sainm.schemapilot.datamove;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DataMoveConcurrencyLimiter {
    private final AtomicInteger activeGlobalJobs = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> activeProjectJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeTableJobs = new ConcurrentHashMap<>();

    public Lease acquire(String projectKey, String tableKey, int projectLimit, int globalLimit, int tableLimit) {
        var safeProjectLimit = Math.max(1, projectLimit);
        var safeGlobalLimit = Math.max(1, globalLimit);
        var safeTableLimit = Math.max(1, tableLimit);
        var projectCounter = activeProjectJobs.computeIfAbsent(projectKey, ignored -> new AtomicInteger());
        var tableCounter = activeTableJobs.computeIfAbsent(tableKey, ignored -> new AtomicInteger());
        while (true) {
            if (activeGlobalJobs.get() < safeGlobalLimit && projectCounter.get() < safeProjectLimit && tableCounter.get() < safeTableLimit) {
                activeGlobalJobs.incrementAndGet();
                projectCounter.incrementAndGet();
                tableCounter.incrementAndGet();
                if (activeGlobalJobs.get() <= safeGlobalLimit && projectCounter.get() <= safeProjectLimit && tableCounter.get() <= safeTableLimit) {
                    return new Lease(projectKey, tableKey);
                }
                release(projectKey, tableKey);
            }
            sleepQuietly();
        }
    }

    private void release(String projectKey, String tableKey) {
        activeGlobalJobs.decrementAndGet();
        var projectCounter = activeProjectJobs.get(projectKey);
        if (projectCounter != null && projectCounter.decrementAndGet() <= 0) {
            activeProjectJobs.remove(projectKey, projectCounter);
        }
        var tableCounter = activeTableJobs.get(tableKey);
        if (tableCounter != null && tableCounter.decrementAndGet() <= 0) {
            activeTableJobs.remove(tableKey, tableCounter);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataMoveException("Interrupted while waiting for data move concurrency slot.", ex);
        }
    }

    public class Lease implements AutoCloseable {
        private final String projectKey;
        private final String tableKey;
        private boolean closed;

        private Lease(String projectKey, String tableKey) {
            this.projectKey = projectKey;
            this.tableKey = tableKey;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release(projectKey, tableKey);
            }
        }
    }
}
