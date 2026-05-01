package org.sainm.schemapilot.datamove;

import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.sainm.schemapilot.sql.SqlIdentifierValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class JdbcOracleStreamingReader implements OracleStreamingReader {
    private final DataSourceConfigService dataSourceConfigService;
    private final OracleLobValueReader lobValueReader;
    private final SqlIdentifierValidator identifierValidator;

    public JdbcOracleStreamingReader(DataSourceConfigService dataSourceConfigService, MemoryBudgetManager memoryBudgetManager, SqlIdentifierValidator identifierValidator) {
        this.dataSourceConfigService = dataSourceConfigService;
        this.lobValueReader = new OracleLobValueReader(memoryBudgetManager);
        this.identifierValidator = identifierValidator;
    }

    @Override
    public long stream(UUID sourceDataSourceId, String sourceTable, List<String> columns, Consumer<TableRow> rowConsumer) {
        return streamSql(sourceDataSourceId, sourceTable, columns, null, rowConsumer);
    }

    @Override
    public long streamShard(UUID sourceDataSourceId, String sourceTable, List<String> columns, DataMoveShard shard, Consumer<TableRow> rowConsumer) {
        return streamSql(sourceDataSourceId, sourceTable, columns, shard.predicate(), rowConsumer);
    }

    @Override
    public long estimateRows(UUID sourceDataSourceId, String sourceTable) {
        try (var connection = dataSourceConfigService.openConnection(sourceDataSourceId);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select count(*) from " + identifierValidator.quoteQualified(sourceTable))) {
            return resultSet.next() ? resultSet.getLong(1) : -1;
        } catch (Exception ex) {
            throw new DataMoveException("Oracle row estimate failed for " + sourceTable + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public NumericBounds numericBounds(UUID sourceDataSourceId, String sourceTable, String shardColumn) {
        var sql = "select min(" + identifierValidator.quote(shardColumn) + "), max(" + identifierValidator.quote(shardColumn) + ") from " + identifierValidator.quoteQualified(sourceTable);
        try (var connection = dataSourceConfigService.openConnection(sourceDataSourceId);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return NumericBounds.unknown();
            }
            var min = resultSet.getObject(1);
            var max = resultSet.getObject(2);
            if (min == null || max == null) {
                return NumericBounds.unknown();
            }
            return new NumericBounds(resultSet.getLong(1), resultSet.getLong(2));
        } catch (Exception ex) {
            return NumericBounds.unknown();
        }
    }

    private long streamSql(UUID sourceDataSourceId, String sourceTable, List<String> columns, String predicate, Consumer<TableRow> rowConsumer) {
        var sql = "select " + columns.stream().map(identifierValidator::quote).collect(Collectors.joining(", ")) + " from " + identifierValidator.quoteQualified(sourceTable);
        if (predicate != null && !predicate.isBlank()) {
            sql += " where " + predicate;
        }
        var count = 0L;
        try (var connection = dataSourceConfigService.openConnection(sourceDataSourceId);
             var statement = connection.createStatement()) {
            statement.setFetchSize(1000);
            try (var resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    var values = new java.util.ArrayList<String>();
                    for (int i = 0; i < columns.size(); i++) {
                        values.add(lobValueReader.read(resultSet.getObject(i + 1)));
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

}
