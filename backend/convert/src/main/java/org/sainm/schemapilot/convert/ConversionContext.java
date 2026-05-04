package org.sainm.schemapilot.convert;

import java.util.UUID;

public record ConversionContext(
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId) {
}
