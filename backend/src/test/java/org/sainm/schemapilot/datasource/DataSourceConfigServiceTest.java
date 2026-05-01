package org.sainm.schemapilot.datasource;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.ai.SensitiveValueRedactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceConfigServiceTest {
    private final PasswordCipher cipher = new PasswordCipher("test-secret");
    private final InMemoryDataSourceConfigRepository repository = new InMemoryDataSourceConfigRepository();
    private final DataSourceConfigService service = new DataSourceConfigService(repository, cipher, new SensitiveValueRedactor());

    @Test
    void storesEncryptedPasswordAndReturnsRedactedConfig() {
        var saved = service.create(new SaveDataSourceConfigRequest(
                "target-pg",
                DataSourceKind.POSTGRESQL,
                "jdbc:postgresql://localhost:5432/schemapilot?password=url-secret",
                "schemapilot",
                "secret-password"
        ));

        var raw = repository.findById(saved.id()).orElseThrow();
        assertThat(saved.passwordConfigured()).isTrue();
        assertThat(saved.toString()).doesNotContain("secret-password");
        assertThat(saved.jdbcUrl()).doesNotContain("url-secret");
        assertThat(raw.jdbcUrl()).contains("url-secret");
        assertThat(raw.encryptedPassword()).doesNotContain("secret-password");
        assertThat(cipher.decrypt(raw.encryptedPassword())).isEqualTo("secret-password");
    }

    @Test
    void rejectsWrongJdbcUrlForKind() {
        assertThatThrownBy(() -> service.create(new SaveDataSourceConfigRequest(
                "bad",
                DataSourceKind.ORACLE,
                "jdbc:postgresql://localhost:5432/schemapilot",
                "user",
                "pwd"
        ))).hasMessageContaining("jdbc:oracle");
    }

    @Test
    void connectionTestFailsWithoutLeakingPassword() {
        var saved = service.create(new SaveDataSourceConfigRequest(
                "missing-oracle",
                DataSourceKind.ORACLE,
                "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
                "system",
                "secret-password"
        ));

        var result = service.test(saved.id());

        assertThat(result.success()).isFalse();
        assertThat(result.risks()).contains("JDBC_DRIVER_MISSING");
        assertThat(result.toString()).doesNotContain("secret-password");
        assertThat(service.list().getFirst().status()).isEqualTo(DataSourceConfigStatus.FAILED);
    }
}
