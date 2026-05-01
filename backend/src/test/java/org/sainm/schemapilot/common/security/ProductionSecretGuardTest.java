package org.sainm.schemapilot.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretGuardTest {
    @Test
    void rejectsDefaultSecretsInProductionProfile() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");
        var guard = new ProductionSecretGuard(
                environment,
                ProductionSecretGuard.DEFAULT_DB_PASSWORD,
                ProductionSecretGuard.DEFAULT_DATASOURCE_ENCRYPTION_KEY
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata database password");
    }

    @Test
    void allowsNonDefaultSecretsInProductionProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var guard = new ProductionSecretGuard(environment, "changed-password", "changed-encryption-key");

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void ignoresDefaultsOutsideProductionProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        var guard = new ProductionSecretGuard(
                environment,
                ProductionSecretGuard.DEFAULT_DB_PASSWORD,
                ProductionSecretGuard.DEFAULT_DATASOURCE_ENCRYPTION_KEY
        );

        assertThatCode(guard::validate).doesNotThrowAnyException();
        assertThat(ProductionSecretGuard.isProductionProfile(new String[]{"prod"})).isTrue();
    }
}
