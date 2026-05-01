package org.sainm.schemapilot.technicalspike;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PgVectorProbeRequest(
        @NotNull
        UUID dataSourceId,
        boolean tryCreateExtension
) {
}
