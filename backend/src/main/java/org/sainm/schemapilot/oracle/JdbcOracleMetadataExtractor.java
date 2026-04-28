package org.sainm.schemapilot.oracle;

import org.sainm.schemapilot.datasource.DataSourceConfigService;
import org.sainm.schemapilot.model.ObjectType;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class JdbcOracleMetadataExtractor implements OracleMetadataExtractor {
    private final DataSourceConfigService dataSourceConfigService;

    public JdbcOracleMetadataExtractor(DataSourceConfigService dataSourceConfigService) {
        this.dataSourceConfigService = dataSourceConfigService;
    }

    @Override
    public OracleScanSnapshot extract(UUID dataSourceId, String schemaName, Consumer<OracleScanProgress> progressConsumer) {
        var risks = new ArrayList<String>();
        try (var connection = dataSourceConfigService.openConnection(dataSourceId)) {
            progressConsumer.accept(new OracleScanProgress(5, "SCHEMA", "Scanning Oracle schemas."));
            var schemas = schemas(connection);
            progressConsumer.accept(new OracleScanProgress(12, "TABLE", "Scanning Oracle tables."));
            var objects = objects(connection, schemaName, risks);
            progressConsumer.accept(new OracleScanProgress(28, "COLUMN", "Scanning Oracle columns."));
            var columns = columns(connection, schemaName, risks);
            progressConsumer.accept(new OracleScanProgress(42, "CONSTRAINT", "Scanning primary keys, foreign keys, unique and check constraints."));
            var constraints = constraints(connection, schemaName, risks);
            progressConsumer.accept(new OracleScanProgress(56, "INDEX", "Scanning indexes."));
            var indexes = indexes(connection, schemaName, risks);
            progressConsumer.accept(new OracleScanProgress(70, "SOURCE", "Scanning DBMS_METADATA and ALL_SOURCE."));
            enrichDdlAndSource(connection, schemaName, objects, risks);
            progressConsumer.accept(new OracleScanProgress(86, "COMMENT_PARTITION", "Scanning comments and partition metadata."));
            enrichCommentsAndPartitions(connection, schemaName, objects, columns, risks);
            return new OracleScanSnapshot(schemaName, schemas, List.copyOf(objects), columns, constraints, indexes, List.copyOf(risks));
        } catch (Exception ex) {
            risks.add("ORACLE_SCAN_FAILED:" + ex.getClass().getSimpleName());
            return new OracleScanSnapshot(schemaName, List.of(), List.of(), List.of(), List.of(), List.of(), List.copyOf(risks));
        }
    }

    private List<String> schemas(Connection connection) throws Exception {
        var schemas = new ArrayList<String>();
        try (var resultSet = connection.getMetaData().getSchemas()) {
            while (resultSet.next()) {
                schemas.add(resultSet.getString("TABLE_SCHEM"));
            }
        }
        return List.copyOf(schemas);
    }

    private List<OracleObjectMetadata> objects(Connection connection, String schemaName, List<String> risks) {
        var objects = new ArrayList<OracleObjectMetadata>();
        query(connection, """
                select object_name, object_type, status
                from all_objects
                where owner = ?
                  and object_type in ('TABLE','VIEW','SEQUENCE','TRIGGER','FUNCTION','PROCEDURE','PACKAGE','PACKAGE BODY','SYNONYM')
                order by object_type, object_name
                """, List.of(schemaName), resultSet -> objects.add(new OracleObjectMetadata(
                objectType(resultSet.getString("object_type")),
                resultSet.getString("object_name"),
                resultSet.getString("status"),
                null,
                null,
                null,
                null
        )), risks, "ALL_OBJECTS_PERMISSION_MISSING");
        return objects;
    }

    private List<OracleColumnMetadata> columns(Connection connection, String schemaName, List<String> risks) {
        var columns = new ArrayList<OracleColumnMetadata>();
        query(connection, """
                select table_name, column_name, data_type, data_precision, data_scale, nullable, data_default
                from all_tab_columns
                where owner = ?
                order by table_name, column_id
                """, List.of(schemaName), resultSet -> columns.add(new OracleColumnMetadata(
                resultSet.getString("table_name"),
                resultSet.getString("column_name"),
                resultSet.getString("data_type"),
                intOrNull(resultSet, "data_precision"),
                intOrNull(resultSet, "data_scale"),
                "Y".equalsIgnoreCase(resultSet.getString("nullable")),
                resultSet.getString("data_default"),
                null
        )), risks, "ALL_TAB_COLUMNS_PERMISSION_MISSING");
        return columns;
    }

    private List<OracleConstraintMetadata> constraints(Connection connection, String schemaName, List<String> risks) {
        var constraints = new LinkedHashMap<String, MutableConstraint>();
        query(connection, """
                select c.constraint_name, c.table_name, c.constraint_type, cc.column_name, c.r_constraint_name, r.table_name referenced_table, rcc.column_name referenced_column, c.search_condition
                from all_constraints c
                left join all_cons_columns cc on cc.owner = c.owner and cc.constraint_name = c.constraint_name
                left join all_constraints r on r.owner = c.r_owner and r.constraint_name = c.r_constraint_name
                left join all_cons_columns rcc on rcc.owner = r.owner and rcc.constraint_name = r.constraint_name and rcc.position = cc.position
                where c.owner = ? and c.constraint_type in ('P','R','U','C')
                order by c.table_name, c.constraint_name, cc.position
                """, List.of(schemaName), resultSet -> {
            var key = resultSet.getString("constraint_name");
            var tableName = resultSet.getString("table_name");
            var constraintType = resultSet.getString("constraint_type");
            var referencedTable = resultSet.getString("referenced_table");
            var searchCondition = resultSet.getString("search_condition");
            var item = constraints.computeIfAbsent(key, ignored -> new MutableConstraint(key, tableName, constraintType, referencedTable, searchCondition));
            addIfPresent(item.columns, resultSet.getString("column_name"));
            addIfPresent(item.referencedColumns, resultSet.getString("referenced_column"));
        }, risks, "ALL_CONSTRAINTS_PERMISSION_MISSING");
        return constraints.values().stream()
                .map(item -> new OracleConstraintMetadata(item.name, item.tableName, item.type, List.copyOf(item.columns), item.referencedTable, List.copyOf(item.referencedColumns), item.searchCondition))
                .toList();
    }

    private List<OracleIndexMetadata> indexes(Connection connection, String schemaName, List<String> risks) {
        var indexes = new LinkedHashMap<String, MutableIndex>();
        query(connection, """
                select i.index_name, i.table_name, i.uniqueness, ic.column_name
                from all_indexes i
                left join all_ind_columns ic on ic.index_owner = i.owner and ic.index_name = i.index_name
                where i.owner = ?
                order by i.table_name, i.index_name, ic.column_position
                """, List.of(schemaName), resultSet -> {
            var key = resultSet.getString("index_name");
            var tableName = resultSet.getString("table_name");
            var unique = "UNIQUE".equalsIgnoreCase(resultSet.getString("uniqueness"));
            var item = indexes.computeIfAbsent(key, ignored -> new MutableIndex(key, tableName, unique));
            addIfPresent(item.columns, resultSet.getString("column_name"));
        }, risks, "ALL_INDEXES_PERMISSION_MISSING");
        return indexes.values().stream()
                .map(item -> new OracleIndexMetadata(item.name, item.tableName, item.unique, List.copyOf(item.columns)))
                .toList();
    }

    private void enrichDdlAndSource(Connection connection, String schemaName, List<OracleObjectMetadata> objects, List<String> risks) {
        var replacements = new ArrayList<OracleObjectMetadata>();
        for (var object : objects) {
            var ddl = dbmsMetadata(connection, object, risks);
            var source = source(connection, schemaName, object, risks);
            replacements.add(new OracleObjectMetadata(object.type(), object.name(), object.status(), ddl, source, object.comment(), object.partitionInfo()));
        }
        objects.clear();
        objects.addAll(replacements);
    }

    private void enrichCommentsAndPartitions(Connection connection, String schemaName, List<OracleObjectMetadata> objects, List<OracleColumnMetadata> columns, List<String> risks) {
        var objectComments = new LinkedHashMap<String, String>();
        query(connection, "select table_name, comments from all_tab_comments where owner = ?", List.of(schemaName), resultSet -> objectComments.put(resultSet.getString("table_name"), resultSet.getString("comments")), risks, "ALL_TAB_COMMENTS_PERMISSION_MISSING");
        var partitionInfo = new LinkedHashMap<String, String>();
        query(connection, "select table_name, partitioning_type from all_part_tables where owner = ?", List.of(schemaName), resultSet -> partitionInfo.put(resultSet.getString("table_name"), resultSet.getString("partitioning_type")), risks, "ALL_PART_TABLES_PERMISSION_MISSING");
        var replacements = objects.stream()
                .map(object -> new OracleObjectMetadata(object.type(), object.name(), object.status(), object.ddl(), object.source(), objectComments.get(object.name()), partitionInfo.get(object.name())))
                .toList();
        objects.clear();
        objects.addAll(replacements);
    }

    private String dbmsMetadata(Connection connection, OracleObjectMetadata object, List<String> risks) {
        var sqlType = switch (object.type()) {
            case PACKAGE_BODY -> "PACKAGE_BODY";
            default -> object.type().name();
        };
        var holder = new StringBuilder();
        query(connection, "select dbms_metadata.get_ddl(?, ?) as ddl from dual", List.of(sqlType, object.name()), resultSet -> holder.append(resultSet.getString("ddl")), risks, "DBMS_METADATA_PERMISSION_MISSING");
        return holder.isEmpty() ? null : holder.toString();
    }

    private String source(Connection connection, String schemaName, OracleObjectMetadata object, List<String> risks) {
        if (!List.of(ObjectType.TRIGGER, ObjectType.FUNCTION, ObjectType.PROCEDURE, ObjectType.PACKAGE, ObjectType.PACKAGE_BODY).contains(object.type())) {
            return null;
        }
        var holder = new StringBuilder();
        query(connection, """
                select text from all_source
                where owner = ? and name = ?
                order by line
                """, List.of(schemaName, object.name()), resultSet -> holder.append(resultSet.getString("text")), risks, "ALL_SOURCE_PERMISSION_MISSING");
        return holder.isEmpty() ? null : holder.toString();
    }

    private void query(Connection connection, String sql, List<String> args, SqlConsumer consumer, List<String> risks, String risk) {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                statement.setString(i + 1, args.get(i));
            }
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    consumer.accept(resultSet);
                }
            }
        } catch (Exception ex) {
            risks.add(risk);
        }
    }

    private ObjectType objectType(String value) {
        return switch (value) {
            case "TABLE" -> ObjectType.TABLE;
            case "VIEW" -> ObjectType.VIEW;
            case "SEQUENCE" -> ObjectType.SEQUENCE;
            case "TRIGGER" -> ObjectType.TRIGGER;
            case "FUNCTION" -> ObjectType.FUNCTION;
            case "PROCEDURE" -> ObjectType.PROCEDURE;
            case "PACKAGE" -> ObjectType.PACKAGE;
            case "PACKAGE BODY" -> ObjectType.PACKAGE_BODY;
            default -> ObjectType.UNKNOWN;
        };
    }

    private Integer intOrNull(ResultSet resultSet, String column) throws Exception {
        var value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private interface SqlConsumer {
        void accept(ResultSet resultSet) throws Exception;
    }

    private record MutableConstraint(String name, String tableName, String type, List<String> columns, String referencedTable, List<String> referencedColumns, String searchCondition) {
        MutableConstraint(String name, String tableName, String type, String referencedTable, String searchCondition) {
            this(name, tableName, type, new ArrayList<>(), referencedTable, new ArrayList<>(), searchCondition);
        }
    }

    private record MutableIndex(String name, String tableName, boolean unique, List<String> columns) {
        MutableIndex(String name, String tableName, boolean unique) {
            this(name, tableName, unique, new ArrayList<>());
        }
    }
}
