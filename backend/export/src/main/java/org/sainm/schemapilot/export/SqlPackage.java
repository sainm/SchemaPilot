package org.sainm.schemapilot.export;

import java.util.UUID;

public record SqlPackage(
        UUID projectId,
        String fileName,
        String content) {
}
