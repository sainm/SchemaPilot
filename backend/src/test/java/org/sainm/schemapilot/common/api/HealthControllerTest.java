package org.sainm.schemapilot.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {
    @Test
    void returnsHealthPayload() {
        var response = new HealthController().health();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("service", "schemapilot-backend");
        assertThat(response.data()).containsEntry("status", "UP");
    }
}
