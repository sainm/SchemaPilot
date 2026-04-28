package org.sainm.schemapilot.datamove;

import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class JdbcOracleStreamingReader implements OracleStreamingReader {
    private final DataSourceConfigService dataSourceConfigService;

    public JdbcOracleStreamingReader(DataSourceConfigService dataSourceConfigService) {
        this.dataSourceConfigService = dataSourceConfigService;
    }

    @Override
    public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
        var sql = "select " + columns.stream().map(this::quote).collect(Collectors.joining(", ")) + " from " + sourceTable;
        var count = 0L;
        try (var connection = dataSourceConfigService.openConnection(sourceDataSourceId);
             var statement = connection.createStatement()) {
            statement.setFetchSize(1000);
            try (var resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    var values = new java.util.ArrayList<String>();
                    for (int i = 0; i < columns.size(); i++) {
                        values.add(resultSet.getString(i + 1));
                    }
                    rowConsumer.accept(new TableRow(List.copyOf(values)));
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            throw new DataMoveException("Oracle streaming reader failed for " + sourceTable + ": " + ex.getMessage(), ex);
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
