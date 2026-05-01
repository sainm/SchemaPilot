package org.sainm.schemapilot.validation;

import org.sainm.schemapilot.datamove.MemoryBudgetManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

@Service
public class ChecksumCostService {
    private final MemoryBudgetManager memoryBudgetManager;

    public ChecksumCostService(MemoryBudgetManager memoryBudgetManager) {
        this.memoryBudgetManager = memoryBudgetManager;
    }

    public ChecksumCostReport benchmark(ChecksumCostRequest request) {
        var rowCount = Math.max(1, request.rowCount());
        var shardCount = Math.max(1, request.shardCount());
        var columnCount = Math.max(1, request.columnCount());
        var valueBytes = Math.max(1, request.valueBytes());
        var buffers = new ArrayList<FfmChecksumBuffer>();
        var started = Instant.now();
        try {
            for (int i = 0; i < shardCount; i++) {
                buffers.add(new FfmChecksumBuffer(memoryBudgetManager, Math.max(1024, memoryBudgetManager.shardBudgetBytes())));
            }
            for (int row = 0; row < rowCount; row++) {
                buffers.get(Math.floorMod(row, shardCount)).appendRow(rowValues(row, columnCount, valueBytes));
            }
            buffers.forEach(FfmChecksumBuffer::value);
        } finally {
            buffers.forEach(FfmChecksumBuffer::close);
        }
        var elapsed = Math.max(1, Duration.between(started, Instant.now()).toMillis());
        var payloadBytes = (long) rowCount * columnCount * valueBytes;
        return new ChecksumCostReport(
                rowCount,
                shardCount,
                elapsed,
                rowCount / (elapsed / 1000.0),
                payloadBytes,
                memoryBudgetManager.reservedBytes(),
                memoryBudgetManager.activeLeases()
        );
    }

    private java.util.List<String> rowValues(int row, int columnCount, int valueBytes) {
        var values = new ArrayList<String>();
        var base = ("row-" + row + "-").repeat(Math.max(1, valueBytes / 8 + 1));
        for (int column = 0; column < columnCount; column++) {
            values.add(base.substring(0, Math.min(valueBytes, base.length())));
        }
        return values;
    }
}
