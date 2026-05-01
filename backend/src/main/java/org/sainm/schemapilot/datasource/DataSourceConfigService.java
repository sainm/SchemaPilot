package org.sainm.schemapilot.datasource;

import org.sainm.schemapilot.ai.SensitiveValueRedactor;
import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class DataSourceConfigService {
    private final DataSourceConfigRepository repository;
    private final PasswordCipher passwordCipher;
    private final SensitiveValueRedactor redactor;

    public DataSourceConfigService(DataSourceConfigRepository repository, PasswordCipher passwordCipher, SensitiveValueRedactor redactor) {
        this.repository = repository;
        this.passwordCipher = passwordCipher;
        this.redactor = redactor;
    }

    public DataSourceConfigResponse create(SaveDataSourceConfigRequest request) {
        validateJdbcUrl(request.kind(), request.jdbcUrl());
        var now = Instant.now();
        return DataSourceConfigResponse.from(repository.save(new DataSourceConfig(
                UUID.randomUUID(),
                request.name(),
                request.kind(),
                request.jdbcUrl(),
                request.username(),
                passwordCipher.encrypt(request.password()),
                DataSourceConfigStatus.DRAFT,
                now,
                now
        )));
    }

    public List<DataSourceConfigResponse> list() {
        return repository.findAll().stream().map(DataSourceConfigResponse::from).toList();
    }

    public DataSourceConnectionTestResult test(UUID configId) {
        var config = find(configId);
        var risks = new ArrayList<String>();
        var permissions = new ArrayList<String>();
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", passwordCipher.decrypt(config.encryptedPassword()));
        try (var connection = DriverManager.getConnection(config.jdbcUrl(), props)) {
            var metadata = connection.getMetaData();
            permissions.add(connection.isReadOnly() ? "READ_ONLY_CONNECTION" : "READ_WRITE_CONNECTION");
            permissions.addAll(permissionProbe(config.kind(), connection));
            repository.save(withStatus(config, DataSourceConfigStatus.TESTED));
            return new DataSourceConnectionTestResult(
                    config.id(),
                    true,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion(),
                    metadata.getUserName(),
                    permissions,
                    risks,
                    "Connection test succeeded.",
                    Instant.now()
            );
        } catch (Exception ex) {
            repository.save(withStatus(config, DataSourceConfigStatus.FAILED));
            return new DataSourceConnectionTestResult(
                    config.id(),
                    false,
                    null,
                    null,
                    config.username(),
                    permissions,
                    List.of(classifyFailure(ex)),
                    redactor.redact(ex.getMessage()),
                    Instant.now()
            );
        }
    }

    public Connection openConnection(UUID configId) throws java.sql.SQLException {
        var config = find(configId);
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", passwordCipher.decrypt(config.encryptedPassword()));
        return DriverManager.getConnection(config.jdbcUrl(), props);
    }

    public DataSourceKind kind(UUID configId) {
        return find(configId).kind();
    }

    private DataSourceConfig find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Datasource config not found: " + id));
    }

    private DataSourceConfig withStatus(DataSourceConfig config, DataSourceConfigStatus status) {
        return new DataSourceConfig(
                config.id(),
                config.name(),
                config.kind(),
                config.jdbcUrl(),
                config.username(),
                config.encryptedPassword(),
                status,
                config.createdAt(),
                Instant.now()
        );
    }

    private void validateJdbcUrl(DataSourceKind kind, String jdbcUrl) {
        if (kind == DataSourceKind.ORACLE && !jdbcUrl.startsWith("jdbc:oracle:")) {
            throw new BadRequestException("Oracle datasource requires a jdbc:oracle URL.");
        }
        if (kind == DataSourceKind.POSTGRESQL && !jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new BadRequestException("PostgreSQL datasource requires a jdbc:postgresql URL.");
        }
    }

    private List<String> permissionProbe(DataSourceKind kind, java.sql.Connection connection) {
        var probes = new ArrayList<String>();
        if (kind == DataSourceKind.POSTGRESQL) {
            probes.add("VERSION_READABLE");
            probes.add("SCHEMA_PRIVILEGE_CHECK_READY");
        } else {
            probes.add("DBMS_METADATA_CHECK_REQUIRED");
            probes.add("ALL_SOURCE_CHECK_REQUIRED");
        }
        return probes;
    }

    private String classifyFailure(Exception ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("no suitable driver")) {
            return "JDBC_DRIVER_MISSING";
        }
        if (message.contains("password") || message.contains("authentication")) {
            return "AUTHENTICATION_FAILED";
        }
        if (message.contains("timeout") || message.contains("refused")) {
            return "NETWORK_UNREACHABLE";
        }
        return "CONNECTION_FAILED";
    }
}
