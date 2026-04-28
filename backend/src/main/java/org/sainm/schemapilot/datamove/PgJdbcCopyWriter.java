package org.sainm.schemapilot.datamove;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class PgJdbcCopyWriter implements PostgresCopyWriter {
    private final DataSourceConfigService dataSourceConfigService;
    private final MemoryBudgetManager memoryBudgetManager;

    public PgJdbcCopyWriter(DataSourceConfigService dataSourceConfigService, MemoryBudgetManager memoryBudgetManager) {
        this.dataSourceConfigService = dataSourceConfigService;
        this.memoryBudgetManager = memoryBudgetManager;
    }

    @Override
    public long copy(UUID targetDataSourceId, String targetTable, List<String> columns, Iterable<TableRow> rows, Consumer<DataMoveProgress> progressConsumer) {
        var sql = "COPY " + targetTable + " (" + columns.stream().map(this::quote).collect(Collectors.joining(", ")) + ") FROM STDIN WITH (FORMAT text)";
        try (var output = new ByteArrayOutputStream();
             var buffer = new FfmCopyBuffer(memoryBudgetManager, Math.max(8192, memoryBudgetManager.shardBudgetBytes()))) {
            var count = 0L;
            for (var row : rows) {
                writeRow(buffer, output, row);
                count++;
                if (count % 1000 == 0) {
                    buffer.flushTo(output);
                    progressConsumer.accept(new DataMoveProgress(count, count));
                }
            }
            buffer.flushTo(output);
            try (var connection = dataSourceConfigService.openConnection(targetDataSourceId)) {
                var pgConnection = connection.unwrap(BaseConnection.class);
                var copyManager = new CopyManager(pgConnection);
                copyManager.copyIn(sql, new ByteArrayInputStream(output.toByteArray()));
            }
            progressConsumer.accept(new DataMoveProgress(count, count));
            return count;
        } catch (Exception ex) {
            throw new DataMoveException("PostgreSQL COPY failed for " + targetTable + ": " + ex.getMessage(), ex);
        }
    }

    private void writeRow(FfmCopyBuffer buffer, ByteArrayOutputStream output, TableRow row) throws java.io.IOException {
        for (int i = 0; i < row.values().size(); i++) {
            if (i > 0) {
                buffer.writeDelimiter();
            }
            try {
                buffer.writeField(row.values().get(i));
            } catch (MemoryBudgetExceededException ex) {
                buffer.flushTo(output);
                buffer.writeField(row.values().get(i));
            }
        }
        buffer.writeRowEnd();
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
