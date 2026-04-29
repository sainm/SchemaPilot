package org.sainm.schemapilot.validation;

import org.sainm.schemapilot.datamove.MemoryBudgetManager;
import org.sainm.schemapilot.datamove.TableRow;
import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JdbcValidationProbe implements ValidationProbe {
    private final DataSourceConfigService dataSourceConfigService;
    private final MemoryBudgetManager memoryBudgetManager;

    public JdbcValidationProbe(DataSourceConfigService dataSourceConfigService, MemoryBudgetManager memoryBudgetManager) {
        this.dataSourceConfigService = dataSourceConfigService;
        this.memoryBudgetManager = memoryBudgetManager;
    }

    @Override
    public boolean objectExists(UUID dataSourceId, String objectName) {
        try (var connection = dataSourceConfigService.openConnection(dataSourceId);
             var statement = connection.createStatement()) {
            statement.executeQuery("select 1 from " + objectName + " where 1 = 0").close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public long rowCount(UUID dataSourceId, String tableName) {
        try (var connection = dataSourceConfigService.openConnection(dataSourceId);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select count(*) from " + tableName)) {
            return resultSet.next() ? resultSet.getLong(1) : -1;
        } catch (Exception ex) {
            throw new ValidationProbeException("Row count validation failed for " + tableName + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<TableRow> sampleRows(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        var sql = "select " + columns.stream().map(this::quote).collect(Collectors.joining(", "))
                + " from " + tableName
                + " order by " + quote(keyColumn)
                + " fetch first " + limit + " rows only";
        try (var connection = dataSourceConfigService.openConnection(dataSourceId);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            var rows = new ArrayList<TableRow>();
            while (resultSet.next()) {
                var values = new ArrayList<String>();
                for (int i = 0; i < columns.size(); i++) {
                    values.add(resultSet.getString(i + 1));
                }
                rows.add(new TableRow(List.copyOf(values)));
            }
            return List.copyOf(rows);
        } catch (Exception ex) {
            throw new ValidationProbeException("Sample row validation failed for " + tableName + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<ShardChecksum> shardChecksums(UUID dataSourceId, String tableName, List<String> columns, String keyColumn, int shardCount) {
        var safeShardCount = Math.max(1, shardCount);
        var buffers = new ArrayList<FfmChecksumBuffer>();
        var rowCounts = new long[safeShardCount];
        try {
            for (int i = 0; i < safeShardCount; i++) {
                buffers.add(new FfmChecksumBuffer(memoryBudgetManager, Math.max(1024, memoryBudgetManager.shardBudgetBytes())));
            }
            streamRows(dataSourceId, tableName, columns, row -> {
                var shardIndex = shardIndex(row, columns, keyColumn, safeShardCount);
                buffers.get(shardIndex).appendRow(row.values());
                rowCounts[shardIndex]++;
            });
            var checksums = new ArrayList<ShardChecksum>();
            for (int i = 0; i < safeShardCount; i++) {
                checksums.add(new ShardChecksum(i, rowCounts[i], buffers.get(i).value()));
            }
            return List.copyOf(checksums);
        } finally {
            buffers.forEach(FfmChecksumBuffer::close);
        }
    }

    @Override
    public boolean viewExecutable(UUID dataSourceId, String viewName) {
        return objectExists(dataSourceId, viewName);
    }

    @Override
    public boolean routineCompiles(UUID dataSourceId, String routineName) {
        try (var connection = dataSourceConfigService.openConnection(dataSourceId)) {
            var database = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (database.contains("oracle")) {
                return oracleRoutineValid(connection, routineName);
            }
            if (database.contains("postgres")) {
                return postgresRoutineExists(connection, routineName);
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private void streamRows(UUID dataSourceId, String tableName, List<String> columns, java.util.function.Consumer<TableRow> consumer) {
        var sql = "select " + columns.stream().map(this::quote).collect(Collectors.joining(", "))
                + " from " + tableName
                + " order by " + quote(columns.get(0));
        try (var connection = dataSourceConfigService.openConnection(dataSourceId);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                var values = new ArrayList<String>();
                for (int i = 0; i < columns.size(); i++) {
                    values.add(resultSet.getString(i + 1));
                }
                consumer.accept(new TableRow(List.copyOf(values)));
            }
        } catch (Exception ex) {
            throw new ValidationProbeException("Checksum validation failed for " + tableName + ": " + ex.getMessage(), ex);
        }
    }

    private int shardIndex(TableRow row, List<String> columns, String keyColumn, int shardCount) {
        var keyIndex = Math.max(0, columns.indexOf(keyColumn));
        var keyValue = row.values().get(keyIndex);
        return Math.floorMod(keyValue == null ? 0 : keyValue.hashCode(), shardCount);
    }

    private boolean oracleRoutineValid(java.sql.Connection connection, String routineName) throws java.sql.SQLException {
        var sql = "select status from all_objects where object_name = upper(?) and object_type in ('FUNCTION','PROCEDURE','PACKAGE','PACKAGE BODY')";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, routineName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && "VALID".equalsIgnoreCase(resultSet.getString(1));
            }
        }
    }

    private boolean postgresRoutineExists(java.sql.Connection connection, String routineName) throws java.sql.SQLException {
        var sql = "select 1 from pg_proc where proname = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, routineName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
