package org.sainm.schemapilot.datamove;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class MemoryBudgetManager {
    private final long projectBudgetBytes;
    private final long taskBudgetBytes;
    private final long shardBudgetBytes;
    private final AtomicLong reservedBytes = new AtomicLong();
    private final AtomicLong activeLeases = new AtomicLong();

    public MemoryBudgetManager(
            @Value("${schemapilot.copy.memory.project-budget-bytes:268435456}") long projectBudgetBytes,
            @Value("${schemapilot.copy.memory.task-budget-bytes:67108864}") long taskBudgetBytes,
            @Value("${schemapilot.copy.memory.shard-budget-bytes:8388608}") long shardBudgetBytes
    ) {
        this.projectBudgetBytes = projectBudgetBytes;
        this.taskBudgetBytes = taskBudgetBytes;
        this.shardBudgetBytes = shardBudgetBytes;
    }

    public MemoryLease reserveShard(long bytes) {
        if (bytes <= 0 || bytes > shardBudgetBytes || bytes > taskBudgetBytes) {
            throw new MemoryBudgetExceededException("Shard memory request exceeds shard/task budget: " + bytes);
        }
        var after = reservedBytes.addAndGet(bytes);
        if (after > projectBudgetBytes) {
            reservedBytes.addAndGet(-bytes);
            throw new MemoryBudgetExceededException("Project off-heap memory budget exhausted.");
        }
        activeLeases.incrementAndGet();
        return new MemoryLease(bytes, this);
    }

    void release(long bytes) {
        reservedBytes.addAndGet(-bytes);
        activeLeases.decrementAndGet();
    }

    public long reservedBytes() {
        return reservedBytes.get();
    }

    public long activeLeases() {
        return activeLeases.get();
    }

    public long projectBudgetBytes() {
        return projectBudgetBytes;
    }

    public long taskBudgetBytes() {
        return taskBudgetBytes;
    }

    public long shardBudgetBytes() {
        return shardBudgetBytes;
    }
}
