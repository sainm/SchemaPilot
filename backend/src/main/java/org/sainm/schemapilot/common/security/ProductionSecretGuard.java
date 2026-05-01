package org.sainm.schemapilot.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ProductionSecretGuard {
    static final String DEFAULT_DB_PASSWORD = "schemapilot";
    static final String DEFAULT_DATASOURCE_ENCRYPTION_KEY = "local-dev-only-change-me";

    private final Environment environment;
    private final String databasePassword;
    private final String datasourceEncryptionKey;

    public ProductionSecretGuard(
            Environment environment,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${schemapilot.datasource.encryption-key:local-dev-only-change-me}") String datasourceEncryptionKey
    ) {
        this.environment = environment;
        this.databasePassword = databasePassword;
        this.datasourceEncryptionKey = datasourceEncryptionKey;
    }

    @PostConstruct
    public void validate() {
        if (!isProductionProfile(environment.getActiveProfiles())) {
            return;
        }
        if (DEFAULT_DB_PASSWORD.equals(databasePassword)) {
            throw new IllegalStateException("Production profile must not use the default metadata database password.");
        }
        if (DEFAULT_DATASOURCE_ENCRYPTION_KEY.equals(datasourceEncryptionKey)) {
            throw new IllegalStateException("Production profile must not use the default datasource encryption key.");
        }
    }

    static boolean isProductionProfile(String[] activeProfiles) {
        return Arrays.stream(activeProfiles)
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }
}
